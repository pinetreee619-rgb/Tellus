package com.yucareux.tellus.integration.voxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VoxyInFlightRequestsTest {
   @Test
   void countsActualRequestsUntilTheirOwnCompletion() {
      VoxyInFlightRequests requests = new VoxyInFlightRequests();
      Object first = requests.begin(42L);
      Object second = requests.begin(-7L);
      assertNotNull(first);
      assertNotNull(second);
      assertNull(requests.begin(42L));
      assertEquals(2, requests.size());
      assertTrue(requests.finish(42L, first));
      assertFalse(requests.finish(42L, first));
      assertEquals(1, requests.size());
      assertTrue(requests.finish(-7L, second));
      assertEquals(0, requests.size());
   }

   @Test
   void staleCompletionCannotReleaseARequestFromANewWorld() {
      VoxyInFlightRequests requests = new VoxyInFlightRequests();
      Object oldToken = requests.begin(42L);
      requests.clear();
      Object newToken = requests.begin(42L);
      assertNotNull(newToken);
      assertFalse(requests.finish(42L, oldToken));
      assertEquals(1, requests.size());
      assertNull(requests.begin(42L));
      assertTrue(requests.finish(42L, newToken));
   }

   @Test
   void concurrentRequestsForOneChunkHaveOnlyOneOwner() throws Exception {
      VoxyInFlightRequests requests = new VoxyInFlightRequests();
      var executor = Executors.newFixedThreadPool(8);
      CountDownLatch start = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(8);
      AtomicInteger owners = new AtomicInteger();
      try {
         for (int i = 0; i < 8; i++) {
            executor.execute(() -> {
               try {
                  start.await();
                  if (requests.begin(42L) != null) {
                     owners.incrementAndGet();
                  }
               } catch (InterruptedException error) {
                  Thread.currentThread().interrupt();
               } finally {
                  done.countDown();
               }
            });
         }
         start.countDown();
         assertTrue(done.await(5, TimeUnit.SECONDS));
         assertEquals(1, owners.get());
         assertEquals(1, requests.size());
      } finally {
         executor.shutdownNow();
      }
   }
}
