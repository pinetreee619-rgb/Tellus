package com.yucareux.tellus.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class RiverGraphPrecisionTest {
   @Test
   void retainsDistinctSubBlockSamplesAtFullEarthScale() {
      for (int origin : new int[]{0, 19_000_000, -19_000_000}) {
         RiverGraph graph = buildSamples(origin, false);
         assertEquals(1, graph.count);
         assertEquals(2, graph.sampleStart[1]);
         assertArrayEquals(new double[]{origin + 0.25, origin + 0.75}, graph.sampleX, 0.0);
         assertArrayEquals(new double[]{origin + 0.25, origin + 0.75}, graph.sampleZ, 0.0);
         assertEquals(origin, Math.round(graph.sampleX[0]));
         assertEquals(origin + 1L, Math.round(graph.sampleX[1]));
      }
   }

   @Test
   void largeCoordinateSampleOrderingIsIndependentOfFeatureOrder() {
      for (int origin : new int[]{19_000_000, -19_000_000}) {
         RiverGraph first = buildSamples(origin, false);
         RiverGraph reversed = buildSamples(origin, true);
         assertArrayEquals(first.sampleX, reversed.sampleX, 0.0);
         assertArrayEquals(first.sampleZ, reversed.sampleZ, 0.0);
         assertArrayEquals(first.sampleDirX, reversed.sampleDirX, 0.0F);
         assertArrayEquals(first.sampleDirZ, reversed.sampleDirZ, 0.0F);
      }
   }

   private static RiverGraph buildSamples(int origin, boolean reverse) {
      RiverGraph.Builder builder = new RiverGraph.Builder(1);
      for (int i = 0; i < 2; i++) {
         int sample = reverse ? 1 - i : i;
         double midpoint = origin + 0.25 + sample * 0.5;
         builder.addLine(new double[]{midpoint - 0.125, midpoint + 0.125},
            new double[]{midpoint, midpoint}, sample, 1.0,
            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
      }
      return builder.build();
   }
}
