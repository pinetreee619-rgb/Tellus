package com.yucareux.tellus.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class SurfacePatchNoiseTest {
   private static final long SALT = 1234567L;

   @Test
   void coverageKeepsTheRequestedAreaShare() {
      for (double coverage : new double[]{10.0, 35.0, 65.0, 90.0}) {
         int covered = 0;
         int samples = 0;
         for (int z = -4096; z < 4096; z += 16) {
            for (int x = -4096; x < 4096; x += 16) {
               if (SurfacePatchNoise.covers(x, z, SALT, coverage)) {
                  covered++;
               }
               samples++;
            }
         }

         assertEquals(coverage, 100.0 * covered / samples, 3.0, "coverage " + coverage);
      }
   }

   @Test
   void neighbouringColumnsFormPatchesInsteadOfSpeckle() {
      int agreeing = 0;
      int pairs = 0;
      for (int z = 0; z < 512; z += 2) {
         for (int x = 0; x < 512; x += 2) {
            boolean here = SurfacePatchNoise.covers(x, z, SALT, 50.0);
            agreeing += here == SurfacePatchNoise.covers(x + 1, z, SALT, 50.0) ? 1 : 0;
            agreeing += here == SurfacePatchNoise.covers(x, z + 1, SALT, 50.0) ? 1 : 0;
            pairs += 2;
         }
      }

      // Independent per-block picks agree half the time at 50% coverage.
      assertTrue(agreeing > pairs * 0.9, "neighbour agreement " + agreeing + "/" + pairs);
   }

   @Test
   void selectionIsStableAndBounded() {
      for (int i = 0; i < 1000; i++) {
         int x = i * 7919 - 3_000_000;
         int z = i * 104729 - 50_000_000;
         double value = SurfacePatchNoise.uniform(x, z, SALT);
         assertTrue(value >= 0.0 && value < 1.0);
         assertEquals(value, SurfacePatchNoise.uniform(x, z, SALT));
         int percent = SurfacePatchNoise.percent(x, z, SALT);
         assertTrue(percent >= 0 && percent <= 99);
      }

      assertFalse(SurfacePatchNoise.covers(5, 5, SALT, 0.0));
      assertFalse(SurfacePatchNoise.covers(5, 5, SALT, Double.NaN));
      assertTrue(SurfacePatchNoise.covers(5, 5, SALT, 100.0));
   }

   @Test
   void differentSaltsGiveIndependentPatterns() {
      int agreeing = 0;
      int samples = 0;
      for (int z = 0; z < 2048; z += 8) {
         for (int x = 0; x < 2048; x += 8) {
            agreeing += SurfacePatchNoise.covers(x, z, SALT, 50.0) == SurfacePatchNoise.covers(x, z, SALT ^ 99L, 50.0) ? 1 : 0;
            samples++;
         }
      }

      assertEquals(0.5, (double)agreeing / samples, 0.1);
   }

   @Test
   void precomputedFadesPreserveTheOriginalFieldExactly() {
      Random random = new Random(93712L);
      for (int i = 0; i < 10_000; i++) {
         int x = random.nextInt();
         int z = random.nextInt();
         long salt = random.nextLong();
         assertEquals(originalField(x, z, salt), SurfacePatchNoise.field(x, z, salt), 0.0);
      }
      for (int x : new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -96, -23, -7, -1, 0, 1, 7, 23, 96, Integer.MAX_VALUE}) {
         for (int z : new int[]{Integer.MIN_VALUE, -97, -1, 0, 1, 97, Integer.MAX_VALUE}) {
            assertEquals(originalField(x, z, SALT), SurfacePatchNoise.field(x, z, SALT), 0.0);
         }
      }
   }

   // Independent reference for the previous interpolation arithmetic.
   private static double originalField(int x, int z, long salt) {
      return originalNoise(x, z, 96, salt) * 0.5
         + originalNoise(x + 37, z - 53, 23, salt ^ -7493989779944505343L) * 0.33
         + originalNoise(x - 71, z + 19, 7, salt ^ 4354685564936845355L) * 0.17;
   }

   private static double originalNoise(int x, int z, int size, long salt) {
      int cellX = Math.floorDiv(x, size);
      int cellZ = Math.floorDiv(z, size);
      double fx = originalFade((double)Math.floorMod(x, size) / (double)size);
      double fz = originalFade((double)Math.floorMod(z, size) / (double)size);
      double nw = originalCell(cellX, cellZ, salt);
      double ne = originalCell(cellX + 1, cellZ, salt);
      double sw = originalCell(cellX, cellZ + 1, salt);
      double se = originalCell(cellX + 1, cellZ + 1, salt);
      double north = nw + fx * (ne - nw);
      double south = sw + fx * (se - sw);
      return north + fz * (south - north);
   }

   private static double originalFade(double value) {
      return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
   }

   private static double originalCell(int x, int z, long salt) {
      long mixed = salt ^ (long)x * 341873128712L ^ (long)z * 132897987541L;
      mixed ^= mixed >>> 33;
      mixed *= -49064778989728563L;
      mixed ^= mixed >>> 33;
      mixed *= -4265267296055464877L;
      mixed ^= mixed >>> 33;
      return (double)(mixed >>> 11) * 0x1.0p-53;
   }
}
