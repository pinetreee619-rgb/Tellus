package com.yucareux.tellus.world.data.elevation;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElevationGridRepairTest {
   @Test
   void interpolatesMissingTileStripWithoutZeroHeightCliffs() {
      double[] elevations = {
         10.0, Double.NaN, Double.NaN, Double.NaN, 50.0,
         20.0, Double.NaN, Double.NaN, Double.NaN, 60.0,
         30.0, Double.NaN, Double.NaN, Double.NaN, 70.0
      };
      boolean[] missing = {
         false, true, true, true, false,
         false, true, true, true, false,
         false, true, true, true, false
      };

      assertTrue(ElevationGridRepair.repairMissing(elevations, missing, 5, 3));
      for (int z = 0; z < 3; z++) {
         int row = z * 5;
         assertTrue(elevations[row] < elevations[row + 1]);
         assertTrue(elevations[row + 1] < elevations[row + 2]);
         assertTrue(elevations[row + 2] < elevations[row + 3]);
         assertTrue(elevations[row + 3] < elevations[row + 4]);
      }
   }

   @Test
   void refusesToInventAnEntirelyMissingGrid() {
      assertFalse(
         ElevationGridRepair.repairMissing(
            new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN},
            new boolean[]{true, true, true, true},
            2,
            2
         )
      );
   }

   @Test
   void repairsFinitePlaceholdersOnlyWhenReachedByValidNeighbors() {
      double[] elevations = new double[36];
      boolean[] missing = new boolean[36];
      Arrays.fill(elevations, 999.0);
      Arrays.fill(missing, true);
      elevations[0] = 10.0;
      missing[0] = false;

      assertTrue(ElevationGridRepair.repairMissing(elevations, missing, 6, 6));
      double[] expected = new double[36];
      Arrays.fill(expected, 10.0);
      assertArrayEquals(expected, elevations, 1.0E-9);
      assertArrayEquals(new boolean[36], missing);
   }

   @Test
   void ignoresMaskedPlaceholdersAndNonFiniteUnmaskedSamples() {
      double[] elevations = {42.0, -999.0, Double.NaN, Double.POSITIVE_INFINITY};
      boolean[] missing = {false, true, false, false};
      assertTrue(ElevationGridRepair.repairMissing(elevations, missing, 2, 2));
      assertArrayEquals(new double[]{42.0, 42.0, 42.0, 42.0}, elevations, 1.0E-9);
      assertArrayEquals(new boolean[4], missing);
   }

   @Test
   void rejectsDimensionsThatOverflowAnIntProduct() {
      assertThrows(IllegalArgumentException.class, () ->
         ElevationGridRepair.repairMissing(new double[0], new boolean[0], 65536, 65536));
   }

   @Test
   void matchesOriginalCardinalInterpolationOnRectangularGrids() {
      Random random = new Random(732L);
      int width = 23;
      int height = 11;
      int[] dx = {-1, 1, 0, 0};
      int[] dz = {0, 0, -1, 1};
      for (int trial = 0; trial < 100; trial++) {
         double[] samples = new double[width * height];
         boolean[] missing = new boolean[samples.length];
         for (int i = 0; i < samples.length; i++) {
            samples[i] = random.nextDouble() * 8000.0 - 1000.0;
            // Ensure at least one original sample in each row, so cardinal repair is enough.
            missing[i] = i % width != 0 && random.nextBoolean();
         }
         double[] expected = samples.clone();
         for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
               int index = x + z * width;
               if (!missing[index]) {
                  continue;
               }
               double weighted = 0.0;
               double totalWeight = 0.0;
               for (int direction = 0; direction < 4; direction++) {
                  for (int distance = 1; ; distance++) {
                     int nx = x + dx[direction] * distance;
                     int nz = z + dz[direction] * distance;
                     if (nx < 0 || nx >= width || nz < 0 || nz >= height) {
                        break;
                     }
                     int candidate = nx + nz * width;
                     if (!missing[candidate]) {
                        double weight = 1.0 / distance;
                        weighted += samples[candidate] * weight;
                        totalWeight += weight;
                        break;
                     }
                  }
               }
               expected[index] = weighted / totalWeight;
            }
         }
         assertTrue(ElevationGridRepair.repairMissing(samples, missing, width, height));
         assertArrayEquals(expected, samples, 0.0);
         assertArrayEquals(new boolean[samples.length], missing);
      }
   }

   @Test
   void interpolatesWideMissingStrip() {
      int width = 4097;
      double[] elevations = new double[width];
      boolean[] missing = new boolean[width];
      Arrays.fill(missing, true);
      elevations[0] = -100.0;
      elevations[width - 1] = 300.0;
      missing[0] = false;
      missing[width - 1] = false;
      assertTrue(ElevationGridRepair.repairMissing(elevations, missing, width, 1));
      for (int x = 0; x < width; x++) {
         assertEquals(-100.0 + 400.0 * x / (width - 1), elevations[x], 1.0E-9);
      }
   }
}
