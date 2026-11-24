package com.yucareux.tellus.world.data.source;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/** Runs preload-only network requests with a conservative global concurrency cap. */
public final class ParallelDownloadRunner {
   public static final String THREAD_NAME_PREFIX = "tellus-preload-download-";
   // Tile requests are latency-bound (CDN and S3 range reads), so more concurrent keep-alive requests
   // raise throughput without much CPU; 24 remains modest for those hosts.
   private static final int THREADS = intProperty("tellus.preload.downloadThreads", 24, 1, 64);
   private static final int MAX_IN_FLIGHT = intProperty("tellus.preload.downloadInFlight", THREADS * 2, THREADS, 128);
   private static final int RECENT_DOWNLOAD_MAX = intProperty("tellus.preload.downloadDedupEntries", 16384, 0, 262144);
   private static final long RECENT_DOWNLOAD_RETENTION_NANOS = TimeUnit.SECONDS.toNanos(
      intProperty("tellus.preload.downloadDedupSeconds", 120, 0, 3600)
   );
   private static final ConcurrentHashMap<DownloadKey, CompletableFuture<Void>> IN_FLIGHT = new ConcurrentHashMap<>();
   private static final RecentDownloads<DownloadKey> RECENTLY_COMPLETED = new RecentDownloads<>(
      RECENT_DOWNLOAD_MAX, RECENT_DOWNLOAD_RETENTION_NANOS, System::nanoTime
   );
   private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(THREADS, new ThreadFactory() {
      private final AtomicInteger index = new AtomicInteger();

      @Override
      public Thread newThread(Runnable runnable) {
         Thread thread = new Thread(runnable, THREAD_NAME_PREFIX + this.index.incrementAndGet());
         thread.setDaemon(true);
         return thread;
      }
   });
   // Items already being downloaded by another request are awaited here instead of on a download
   // thread, so duplicate requests never occupy the bounded download pool.
   private static final ExecutorService SHARED_WAITERS = Executors.newCachedThreadPool(new ThreadFactory() {
      private final AtomicInteger index = new AtomicInteger();

      @Override
      public Thread newThread(Runnable runnable) {
         Thread thread = new Thread(runnable, "tellus-preload-wait-" + this.index.incrementAndGet());
         thread.setDaemon(true);
         return thread;
      }
   });

   private ParallelDownloadRunner() {
   }

   public static <T> int run(
      Collection<T> requestedItems,
      int completedUnits,
      ParallelDownloadRunner.CheckedConsumer<T> downloader,
      ParallelDownloadRunner.ProgressListener<T> progress
   ) {
      return run(null, requestedItems, completedUnits, downloader, progress);
   }

   /**
    * Runs downloads while sharing identical source items across overlapping preload requests.
    * The scope must include every cache generation that can invalidate the requested items.
    */
   public static <T> int run(
      DownloadScope scope,
      Collection<T> requestedItems,
      int completedUnits,
      ParallelDownloadRunner.CheckedConsumer<T> downloader,
      ParallelDownloadRunner.ProgressListener<T> progress
   ) {
      Objects.requireNonNull(requestedItems, "requestedItems");
      Objects.requireNonNull(downloader, "downloader");
      Objects.requireNonNull(progress, "progress");
      LinkedHashSet<T> items = new LinkedHashSet<>(requestedItems);
      if (items.isEmpty()) {
         return completedUnits;
      }

      DownloadProgressReporter.Listener reporter = DownloadProgressReporter.currentListener();
      java.util.concurrent.BlockingQueue<Future<T>> settledQueue = new java.util.concurrent.LinkedBlockingQueue<>();
      ExecutorCompletionService<T> completion = new ExecutorCompletionService<>(EXECUTOR, settledQueue);
      ExecutorCompletionService<T> sharedCompletion = new ExecutorCompletionService<>(SHARED_WAITERS, settledQueue);
      List<Future<T>> futures = new ArrayList<>(Math.min(items.size(), MAX_IN_FLIGHT));
      List<T> pendingItems = new ArrayList<>(items.size());
      int finished = 0;
      if (scope != null) {
         for (T item : items) {
            if (RECENTLY_COMPLETED.contains(new DownloadKey(scope, item))) {
               finished++;
               progress.completed(item, completedUnits + finished, items.size());
            } else {
               pendingItems.add(item);
            }
         }
      } else {
         pendingItems.addAll(items);
      }

      Iterator<T> pending = pendingItems.iterator();
      try {
         while (pending.hasNext() && futures.size() < MAX_IN_FLIGHT) {
            futures.add(submit(completion, sharedCompletion, scope, pending.next(), downloader, reporter));
         }

         int settled = finished;
         Throwable firstFailure = null;
         while (settled < items.size()) {
            if (Thread.currentThread().isInterrupted()) {
               throw new InterruptedException("Terrain preload interrupted");
            }

            Future<T> completedFuture = completion.take();
            futures.remove(completedFuture);
            settled++;
            try {
               T item = completedFuture.get();
               finished++;
               progress.completed(item, completedUnits + finished, items.size());
            } catch (ExecutionException error) {
               Throwable cause = error.getCause();
               if (cause instanceof Error fatal) {
                  cancel(futures);
                  throw fatal;
               }
               if (firstFailure == null) {
                  firstFailure = cause;
               }
            }
            if (pending.hasNext()) {
               futures.add(submit(completion, sharedCompletion, scope, pending.next(), downloader, reporter));
            }
         }

         if (firstFailure instanceof RuntimeException runtime) {
            throw runtime;
         }
         if (firstFailure != null) {
            throw new RuntimeException("Terrain download failed", firstFailure);
         }
         return completedUnits + finished;
      } catch (InterruptedException error) {
         Thread.currentThread().interrupt();
         cancel(futures);
         throw new CancellationException("Terrain preload interrupted");
      } catch (RuntimeException | Error error) {
         cancel(futures);
         throw error;
      }
   }

   private static <T> Future<T> submit(
      ExecutorCompletionService<T> completion,
      ExecutorCompletionService<T> sharedCompletion,
      DownloadScope scope,
      T item,
      ParallelDownloadRunner.CheckedConsumer<T> downloader,
      DownloadProgressReporter.Listener reporter
   ) {
      boolean shared = scope != null && IN_FLIGHT.containsKey(new DownloadKey(scope, item));
      return (shared ? sharedCompletion : completion).submit(() -> {
         ParallelDownloadRunner.CheckedConsumer<T> reportedDownloader = requestedItem -> {
            if (reporter == null) {
               downloader.accept(requestedItem);
            } else {
               DownloadProgressReporter.Scope progressScope = DownloadProgressReporter.push(reporter);
               try (progressScope) {
                  downloader.accept(requestedItem);
               }
            }
         };
         if (scope == null) {
            reportedDownloader.accept(item);
         } else {
            downloadOnce(new DownloadKey(scope, item), item, reportedDownloader);
         }
         return item;
      });
   }

   private static <T> void downloadOnce(
      DownloadKey key, T item, ParallelDownloadRunner.CheckedConsumer<T> downloader
   ) throws Exception {
      if (RECENTLY_COMPLETED.contains(key)) {
         return;
      }

      CompletableFuture<Void> owned = new CompletableFuture<>();
      CompletableFuture<Void> existing = IN_FLIGHT.putIfAbsent(key, owned);
      if (existing != null) {
         awaitSharedDownload(existing);
         return;
      }

      try {
         // Close the small race between the first recent-cache check and acquiring ownership.
         if (!RECENTLY_COMPLETED.contains(key)) {
            downloader.accept(item);
            RECENTLY_COMPLETED.remember(key);
         }
         owned.complete(null);
      } catch (Exception | Error error) {
         owned.completeExceptionally(error);
         throw error;
      } finally {
         IN_FLIGHT.remove(key, owned);
      }
   }

   private static void awaitSharedDownload(CompletableFuture<Void> future) throws Exception {
      try {
         future.get();
      } catch (InterruptedException error) {
         Thread.currentThread().interrupt();
         throw error;
      } catch (ExecutionException error) {
         Throwable cause = error.getCause();
         if (cause instanceof Exception exception) {
            throw exception;
         }
         if (cause instanceof Error fatal) {
            throw fatal;
         }
         throw new RuntimeException("Shared terrain download failed", cause);
      }
   }

   /**
    * Keeps completion order and membership in one bounded collection. The clock is sampled
    * under the same lock as insertion so eviction order cannot diverge from completion order.
    * No network operations run while holding this lock.
    */
   static final class RecentDownloads<K> {
      private final int capacity;
      private final long retentionNanos;
      private final LongSupplier clock;
      private final LinkedHashMap<K, Long> completed = new LinkedHashMap<>();

      RecentDownloads(int capacity, long retentionNanos, LongSupplier clock) {
         this.capacity = capacity;
         this.retentionNanos = retentionNanos;
         this.clock = Objects.requireNonNull(clock, "clock");
      }

      synchronized boolean contains(K key) {
         if (this.capacity <= 0 || this.retentionNanos <= 0L) {
            return false;
         }
         long now = this.clock.getAsLong();
         this.evictExpired(now);
         return this.completed.containsKey(key);
      }

      synchronized void remember(K key) {
         if (this.capacity <= 0 || this.retentionNanos <= 0L) {
            return;
         }
         long now = this.clock.getAsLong();
         this.evictExpired(now);
         // Refreshing an existing key moves it to the newest position.
         this.completed.remove(key);
         this.completed.put(key, now);
         if (this.completed.size() > this.capacity) {
            Iterator<K> oldest = this.completed.keySet().iterator();
            oldest.next();
            oldest.remove();
         }
      }

      private void evictExpired(long now) {
         Iterator<Map.Entry<K, Long>> entries = this.completed.entrySet().iterator();
         while (entries.hasNext()) {
            if (now - entries.next().getValue() <= this.retentionNanos) {
               break;
            }
            entries.remove();
         }
      }
   }

   private static void cancel(List<? extends Future<?>> futures) {
      // Only queued items are dropped. A running item may be shared with other requests and is
      // writing a cache file or decoding a tile, so interrupting it would fail those requests and
      // can corrupt decoder state; it finishes within its own timeouts and stays cached.
      for (Future<?> future : futures) {
         future.cancel(false);
      }
   }

   private static int intProperty(String key, int defaultValue, int min, int max) {
      String value = System.getProperty(key);
      if (value == null) {
         return Math.max(min, Math.min(max, defaultValue));
      }
      try {
         return Math.max(min, Math.min(max, Integer.parseInt(value.trim())));
      } catch (NumberFormatException ignored) {
         return Math.max(min, Math.min(max, defaultValue));
      }
   }

   public static DownloadScope scope(String source, long... cacheGenerations) {
      Objects.requireNonNull(source, "source");
      List<Long> generations = Arrays.stream(cacheGenerations).boxed().toList();
      return new DownloadScope(source, generations);
   }

   public record DownloadScope(String source, List<Long> cacheGenerations) {
      public DownloadScope {
         Objects.requireNonNull(source, "source");
         cacheGenerations = List.copyOf(cacheGenerations);
      }
   }

   private record DownloadKey(DownloadScope scope, Object item) {
      private DownloadKey {
         Objects.requireNonNull(scope, "scope");
         Objects.requireNonNull(item, "item");
      }
   }

   @FunctionalInterface
   public interface CheckedConsumer<T> {
      void accept(T item) throws Exception;
   }

   @FunctionalInterface
   public interface ProgressListener<T> {
      void completed(T item, int completedUnits, int phaseTotal);
   }
}
