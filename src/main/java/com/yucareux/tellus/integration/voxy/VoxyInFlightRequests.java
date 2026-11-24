package com.yucareux.tellus.integration.voxy;

import java.util.concurrent.ConcurrentHashMap;

/** Tracks actual chunk requests, with identity tokens to reject obsolete callbacks. */
final class VoxyInFlightRequests {
   private final ConcurrentHashMap<Long, Object> requests = new ConcurrentHashMap<>();

   Object begin(long key) {
      Object token = new Object();
      return this.requests.putIfAbsent(key, token) == null ? token : null;
   }

   boolean finish(long key, Object token) {
      return this.requests.remove(key, token);
   }

   int size() {
      return this.requests.size();
   }

   void clear() {
      this.requests.clear();
   }
}
