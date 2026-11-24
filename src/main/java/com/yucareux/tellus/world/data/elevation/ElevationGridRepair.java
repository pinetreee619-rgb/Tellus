package com.yucareux.tellus.world.data.elevation;

import java.util.Arrays;

/** Repairs the rare preview-only case where one or more elevation tiles remain unavailable after retrying. */
public final class ElevationGridRepair {
   private ElevationGridRepair() {
   }

   public static boolean repairMissing(double[] elevations, boolean[] missing, int width, int height) {
      if (elevations == null || missing == null || width <= 0 || height <= 0) {
         throw new IllegalArgumentException("Invalid elevation grid");
      }
      if ((long)width * height != elevations.length || missing.length != elevations.length) {
         throw new IllegalArgumentException("Elevation grid dimensions do not match its storage");
      }
      int size = elevations.length;

      boolean anyMissing = false;
      boolean anyValid = false;
      for (int index = 0; index < size; index++) {
         if (missing[index] || !Double.isFinite(elevations[index])) {
            missing[index] = true;
            anyMissing = true;
         } else {
            anyValid = true;
         }
      }
      if (!anyMissing) {
         return true;
      }
      if (!anyValid) {
         return false;
      }

      boolean[] unresolved = Arrays.copyOf(missing, size);
      double[] weights = new double[size];
      // Store sums only in missing cells, leaving the original valid samples untouched.
      for (int index = 0; index < size; index++) {
         if (missing[index]) {
            elevations[index] = 0.0;
         }
      }
      // Each pass finds the nearest original sample in one cardinal direction.
      // Keeping left, right, up, down order preserves the original weighted arithmetic.
      for (int z = 0; z < height; z++) {
         int row = z * width;
         accumulateLine(elevations, missing, weights, row, 1, width);
         accumulateLine(elevations, missing, weights, row + width - 1, -1, width);
      }
      for (int x = 0; x < width; x++) {
         accumulateLine(elevations, missing, weights, x, width, height);
         accumulateLine(elevations, missing, weights, x + (height - 1) * width, -width, height);
      }
      boolean needsFallback = false;
      for (int index = 0; index < size; index++) {
         if (missing[index]) {
            if (weights[index] > 0.0) {
               elevations[index] /= weights[index];
               unresolved[index] = false;
            } else {
               needsFallback = true;
            }
         }
      }
      if (!needsFallback) {
         Arrays.fill(missing, false);
         return true;
      }

      // Handles unusual diagonal-only coverage without introducing a zero-height patch.
      // Reuse the weights array as scratch storage for the simultaneous repair passes.
      double[] next = weights;
      while (true) {
         // Only cells actually reached in this pass may become resolved. A missing cell
         // can still contain a finite placeholder, which must never be treated as a sample.
         Arrays.fill(next, Double.NaN);
         int repaired = 0;
         for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
               int index = x + z * width;
               if (!unresolved[index]) {
                  continue;
               }
               double sum = 0.0;
               int count = 0;
               for (int dz = -1; dz <= 1; dz++) {
                  int neighborZ = z + dz;
                  if (neighborZ < 0 || neighborZ >= height) {
                     continue;
                  }
                  for (int dx = -1; dx <= 1; dx++) {
                     int neighborX = x + dx;
                     if ((dx == 0 && dz == 0) || neighborX < 0 || neighborX >= width) {
                        continue;
                     }
                     int neighbor = neighborX + neighborZ * width;
                     if (!unresolved[neighbor]) {
                        sum += elevations[neighbor];
                        count++;
                     }
                  }
               }
               if (count > 0) {
                  next[index] = sum / count;
                  repaired++;
               }
            }
         }
         if (repaired == 0) {
            break;
         }
         for (int index = 0; index < size; index++) {
            if (unresolved[index] && Double.isFinite(next[index])) {
               elevations[index] = next[index];
               unresolved[index] = false;
            }
         }
      }

      for (int index = 0; index < size; index++) {
         if (unresolved[index]) {
            return false;
         }
         missing[index] = false;
      }
      return true;
   }

   private static void accumulateLine(
      double[] elevations, boolean[] missing, double[] weights, int start, int stride, int length
   ) {
      int lastValid = -1;
      double lastElevation = 0.0;
      for (int position = 0, index = start; position < length; position++, index += stride) {
         if (!missing[index]) {
            lastValid = position;
            lastElevation = elevations[index];
         } else if (lastValid >= 0) {
            double weight = 1.0 / (position - lastValid);
            elevations[index] += lastElevation * weight;
            weights[index] += weight;
         }
      }
   }
}
