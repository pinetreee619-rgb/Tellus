package com.yucareux.tellus.world.data.source;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DownloadProgressReporterTest {
   @Test
   void boundedReadAcceptsTheExactLimit() throws IOException {
      byte[] data = new byte[]{1, 2, 3, 4};

      assertArrayEquals(data, DownloadProgressReporter.readAllBytesWithProgress(new ByteArrayInputStream(data), data.length));
   }

   @Test
   void boundedReadRejectsAnOversizedResponse() {
      byte[] data = new byte[]{1, 2, 3, 4, 5};

      assertThrows(
         IOException.class,
         () -> DownloadProgressReporter.readAllBytesWithProgress(new ByteArrayInputStream(data), data.length - 1)
      );
   }

   @Test
   void reportsOnlyAcceptedBytesAndPreservesTheLimit() throws IOException {
      AtomicInteger reported = new AtomicInteger();
      try (DownloadProgressReporter.Scope scope = DownloadProgressReporter.push(new DownloadProgressReporter.Listener() {
         @Override
         public void onRequestStarted(long expectedBytes) {}

         @Override
         public void onBytesRead(int bytes) {
            reported.addAndGet(bytes);
         }

         @Override
         public void onRequestFinished() {}
      })) {
         byte[] data = new byte[9000];
         assertArrayEquals(data, DownloadProgressReporter.readAllBytesWithProgress(new ByteArrayInputStream(data), data.length));
         assertEquals(data.length, reported.get());
         reported.set(0);
         assertThrows(IOException.class, () ->
            DownloadProgressReporter.readAllBytesWithProgress(new ByteArrayInputStream(data), 8192));
         assertEquals(8192, reported.get());
      }
   }
}
