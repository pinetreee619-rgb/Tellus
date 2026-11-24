package com.yucareux.tellus.world.data.source;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Rejects oversized HTTP bodies before forwarding bytes to the collecting subscriber. */
public final class BoundedHttpBody {
   private BoundedHttpBody() {
   }

   public static HttpResponse.BodyHandler<byte[]> handler(int maximumBytes, String label) {
      if (maximumBytes <= 0) throw new IllegalArgumentException("Body limit must be positive");
      Objects.requireNonNull(label, "label");
      return info -> new LimitedSubscriber(maximumBytes, label);
   }

   private static final class LimitedSubscriber implements HttpResponse.BodySubscriber<byte[]> {
      private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
      private final int maximumBytes;
      private final String label;
      private Flow.Subscription subscription;
      private long received;
      private boolean done;

      private LimitedSubscriber(int maximumBytes, String label) {
         this.maximumBytes = maximumBytes;
         this.label = label;
      }

      @Override
      public CompletionStage<byte[]> getBody() {
         return this.delegate.getBody();
      }

      @Override
      public void onSubscribe(Flow.Subscription subscription) {
         this.subscription = subscription;
         this.delegate.onSubscribe(subscription);
      }

      @Override
      public void onNext(List<ByteBuffer> buffers) {
         if (this.done) return;
         for (ByteBuffer buffer : buffers) {
            this.received += buffer.remaining();
            if (this.received > this.maximumBytes) {
               this.done = true;
               try {
                  this.subscription.cancel();
               } finally {
                  this.delegate.onError(new IOException(this.label + " exceeds the " + this.maximumBytes + " byte safety limit"));
               }
               return;
            }
         }
         this.delegate.onNext(buffers);
      }

      @Override
      public void onError(Throwable error) {
         if (!this.done) {
            this.done = true;
            this.delegate.onError(error);
         }
      }

      @Override
      public void onComplete() {
         if (!this.done) {
            this.done = true;
            this.delegate.onComplete();
         }
      }
   }
}
