package com.yucareux.tellus.world.data.source;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class BoundedHttpBodyTest {
   @Test
   void acceptsExactlyTheLimitAcrossMultipleBuffers() {
      var subscriber = BoundedHttpBody.handler(4, "test tile").apply(null);
      var subscription = new TestSubscription();
      subscriber.onSubscribe(subscription);
      subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2}), ByteBuffer.wrap(new byte[]{3})));
      subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{4})));
      subscriber.onComplete();
      assertArrayEquals(new byte[]{1, 2, 3, 4}, subscriber.getBody().toCompletableFuture().join());
      assertFalse(subscription.cancelled);
   }

   @Test
   void cancelsBeforeCollectingOversizedBatch() {
      var subscriber = BoundedHttpBody.handler(4, "test tile").apply(null);
      var subscription = new TestSubscription();
      subscriber.onSubscribe(subscription);
      subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2})));
      var first = ByteBuffer.wrap(new byte[]{3, 4});
      var excess = ByteBuffer.wrap(new byte[]{5});
      subscriber.onNext(List.of(first, excess));
      subscriber.onComplete();
      assertTrue(subscription.cancelled);
      assertTrue(first.position() == 0 && excess.position() == 0);
      var error = assertThrows(CompletionException.class, () -> subscriber.getBody().toCompletableFuture().join());
      assertInstanceOf(IOException.class, error.getCause());
   }

   @Test
   void rejectsInvalidLimits() {
      assertThrows(IllegalArgumentException.class, () -> BoundedHttpBody.handler(0, "test tile"));
   }

   private static final class TestSubscription implements Flow.Subscription {
      boolean cancelled;
      @Override public void request(long count) {}
      @Override public void cancel() { this.cancelled = true; }
   }
}
