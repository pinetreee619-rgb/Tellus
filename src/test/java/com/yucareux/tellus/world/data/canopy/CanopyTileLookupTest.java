package com.yucareux.tellus.world.data.canopy;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanopyTileLookupTest {
   @Test
   void onlineCallerRetriesLocalMissAndCachesSuccessfulResult() throws Exception {
      Cache<String, String> cache = CacheBuilder.newBuilder().build();
      AtomicInteger attempts = new AtomicInteger();
      String result = CanopyTileLookup.get(cache, "tile", () -> {
         if (attempts.incrementAndGet() == 1) throw new CanopyTileLookup.LocalCacheMiss();
         return "decoded";
      }, () -> true);
      assertEquals("decoded", result);
      assertEquals("decoded", cache.getIfPresent("tile"));
      assertEquals(2, attempts.get());
   }

   @Test
   void offlineCallerDoesNotRetryOrPopulateCache() {
      Cache<String, String> cache = CacheBuilder.newBuilder().build();
      AtomicInteger attempts = new AtomicInteger();
      var error = assertThrows(ExecutionException.class, () ->
         CanopyTileLookup.get(cache, "tile", () -> {
            attempts.incrementAndGet();
            throw new CanopyTileLookup.LocalCacheMiss();
         }, () -> false));
      assertInstanceOf(CanopyTileLookup.LocalCacheMiss.class, error.getCause());
      assertEquals(1, attempts.get());
      assertEquals(0L, cache.size());
   }

   @Test
   void realServiceFailureIsNotRetriedBySharedMissHandler() {
      Cache<String, String> cache = CacheBuilder.newBuilder().build();
      AtomicInteger attempts = new AtomicInteger();
      var error = assertThrows(ExecutionException.class, () ->
         CanopyTileLookup.get(cache, "tile", () -> {
            attempts.incrementAndGet();
            throw new IOException("service unavailable");
         }, () -> true));
      assertEquals("service unavailable", error.getCause().getMessage());
      assertEquals(1, attempts.get());
   }
}
