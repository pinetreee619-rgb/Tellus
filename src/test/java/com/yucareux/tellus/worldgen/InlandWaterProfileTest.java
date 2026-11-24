package com.yucareux.tellus.worldgen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InlandWaterProfileTest {
   @Test
   void bankBenchesRecoverBaseHeightAndNeverRaiseTerrain() {
      for (int x = -200; x <= 200; x += 17) {
         for (int z = -200; z <= 200; z += 19) {
            for (int radius : new int[]{3, 5, 15, 40}) {
               int previous = 80;
               for (int d = 0; d <= radius; d++) {
                  int height = InlandWaterProfile.bankSurface(100, 80, d, radius, true, x, z);
                  assertTrue(height >= previous && height <= 100);
                  previous = height;
               }
               assertEquals(100, previous);
               assertEquals(79, InlandWaterProfile.bankSurface(79, 80, 2, radius, true, x, z));
            }
         }
      }
   }

   @Test
   void riverShoalsPreserveShallowEdgesAndBoundDepth() {
      for (int x = -256; x <= 256; x += 7) {
         for (int z = -256; z <= 256; z += 9) {
            assertEquals(1, InlandWaterProfile.riverDepth(0.5, x, z, 6, 3));
            for (int d = 1; d <= 30; d++) {
               int depth = InlandWaterProfile.riverDepth(d, x, z, 6, 3);
               assertTrue(depth >= 1 && depth <= 6);
            }
         }
      }
   }
}
