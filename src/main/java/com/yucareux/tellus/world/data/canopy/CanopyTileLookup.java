package com.yucareux.tellus.world.data.canopy;

import com.google.common.cache.Cache;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;

/** A cache-only miss must not poison an online caller sharing the same load. */
final class CanopyTileLookup {
   private CanopyTileLookup() {
   }

   static <K, V> V get(Cache<K, V> cache, K key, Callable<V> loader, BooleanSupplier mayFetch)
      throws ExecutionException {
      for (int attempt = 0; ; attempt++) {
         try {
            return cache.get(key, loader);
         } catch (ExecutionException failure) {
            if (attempt == 0 && failure.getCause() instanceof LocalCacheMiss
               && !Thread.currentThread().isInterrupted() && mayFetch.getAsBoolean()) {
               // The shared local-only loader has failed. Let this caller initiate
               // a fresh load using its own (online) policy.
               continue;
            }
            throw failure;
         }
      }
   }

   static final class LocalCacheMiss extends IOException {
      LocalCacheMiss() {
         super("ETH canopy tile is not in the local cache");
      }
   }
}
