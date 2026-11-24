package com.yucareux.tellus.world.data.source;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.function.IntConsumer;

/** Bounded reads for network responses and cache files with untrusted lengths. */
public final class InputStreamSafety {
   private InputStreamSafety() {
   }

   public static byte[] readAllBytes(InputStream input, int maximumBytes, String label) throws IOException {
      return readAllBytes(input, maximumBytes, label, ignored -> {});
   }

   static byte[] readAllBytes(InputStream input, int maximumBytes, String label, IntConsumer progress) throws IOException {
      Objects.requireNonNull(input, "input");
      Objects.requireNonNull(label, "label");
      if (maximumBytes <= 0) {
         throw new IllegalArgumentException("Input limit must be positive");
      }
      ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(8192, maximumBytes));
      byte[] buffer = new byte[Math.min(8192, maximumBytes)];
      int total = 0;
      while (true) {
         // Read at most one byte beyond the permitted length to distinguish exact-limit EOF
         // from an oversized response, without consuming a large unwanted network payload.
         int requested = (int)Math.min(buffer.length, (long)maximumBytes - total + 1L);
         int read = input.read(buffer, 0, requested);
         if (read == -1) {
            break;
         }
         if (read == 0) {
            // Some stream wrappers return zero despite a nonempty request. Force progress
            // with a single-byte read rather than spinning indefinitely.
            int next = input.read();
            if (next == -1) {
               break;
            }
            buffer[0] = (byte)next;
            read = 1;
         }
         if (read > maximumBytes - total) {
            throw new IOException(label + " exceeds the " + maximumBytes + " byte safety limit");
         }
         output.write(buffer, 0, read);
         total += read;
         progress.accept(read);
      }
      return output.toByteArray();
   }
}
