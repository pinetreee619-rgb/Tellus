package com.yucareux.tellus.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OceanCoastFieldTest {
   @Test
   void defaultHaloIncludesAllCorrectionDependenciesAtFullScale() {
      assertEquals(657, OceanCoastField.correctionHaloCells(512, 1, 1.0));
      assertEquals(23, OceanCoastField.correctionHaloCells(17, 1, 30.0));
      assertEquals(12, OceanCoastField.correctionHaloCells(512, 64, 1.0));
   }

   @Test
   void shortTransitionLimitsTheBathymetryTriggerReach() {
      assertEquals(32, OceanCoastField.correctionHaloCells(5, 1, 1.0));
      assertEquals(6, OceanCoastField.correctionHaloCells(5, 8, 1.0));
   }

   @Test
   void equalDistanceSeedsHaveTheSamePreferenceAcrossTranslatedWindows() {
      // Two world seeds: (x=5,z=7) and (x=2,z=8).
      int firstA = 7 * 16 + 5;
      int secondA = 8 * 16 + 2;
      int firstB = (7 + 3) * 32 + 5 + 9;
      int secondB = (8 + 3) * 32 + 2 + 9;
      assertTrue(OceanCoastField.prefersCoastSeed(firstA, secondA));
      assertTrue(OceanCoastField.prefersCoastSeed(firstB, secondB));
      assertFalse(OceanCoastField.prefersCoastSeed(secondA, firstA));
      assertFalse(OceanCoastField.prefersCoastSeed(firstA, firstA));
      assertFalse(OceanCoastField.prefersCoastSeed(-1, firstA));
      assertTrue(OceanCoastField.prefersCoastSeed(firstA, -1));
   }
}
