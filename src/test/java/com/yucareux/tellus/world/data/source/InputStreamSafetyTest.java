package com.yucareux.tellus.world.data.source;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class InputStreamSafetyTest {
   @Test
   void acceptsTheExactLimitAndRejectsOneByteMore() throws IOException {
      byte[] data = new byte[]{1, 2, 3, 4};

      assertArrayEquals(data, InputStreamSafety.readAllBytes(new ByteArrayInputStream(data), 4, "test"));
      assertThrows(
         IOException.class,
         () -> InputStreamSafety.readAllBytes(new ByteArrayInputStream(data), 3, "test")
      );
   }

   @Test
   void oversizedReadConsumesOnlyOneByteBeyondTheLimit() {
      ByteArrayInputStream input = new ByteArrayInputStream(new byte[8192]);
      assertThrows(IOException.class, () -> InputStreamSafety.readAllBytes(input, 4, "test"));
      assertEquals(8192 - 5, input.available());
   }

   @Test
   void zeroLengthBulkReadsStillMakeProgress() throws IOException {
      ByteArrayInputStream delegate = new ByteArrayInputStream(new byte[]{1, 2, 3});
      InputStream input = new InputStream() {
         @Override
         public int read(byte[] bytes, int offset, int length) {
            return 0;
         }

         @Override
         public int read() {
            return delegate.read();
         }
      };
      assertArrayEquals(new byte[]{1, 2, 3}, InputStreamSafety.readAllBytes(input, 3, "test"));
   }

   @Test
   void acceptsEmptyStream() throws IOException {
      assertArrayEquals(new byte[0], InputStreamSafety.readAllBytes(new ByteArrayInputStream(new byte[0]), 1, "test"));
   }
}
