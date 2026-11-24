package com.yucareux.tellus.world.data.elevation;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
