package com.yucareux.tellus.world.data.canopy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yucareux.tellus.worldgen.EarthProjection;
import org.junit.jupiter.api.Test;

class TellusCanopyHeightSourceTest {
   @Test
   void keepsNativeTilesForFullDetailAtThirtyMeterScale() {
      assertEquals(13, TellusCanopyHeightSource.levelForResolution(1.0, 1.0));
      assertEquals(13, TellusCanopyHeightSource.levelForResolution(30.0, 30.0));
      assertEquals(12, TellusCanopyHeightSource.levelForResolution(60.0, 60.0));
      assertEquals(11, TellusCanopyHeightSource.levelForResolution(200.0, 200.0));
   }

   @Test
   void mapsGeographicCoordinatesToTheArcGisTileGrid() {
      assertEquals(new TellusCanopyHeightSource.TileKey(13, 3937, 8437), TellusCanopyHeightSource.tileKey(0.0, 0.0, 13));
      assertNull(TellusCanopyHeightSource.tileKey(0.0, 84.0, 13));
      assertNull(TellusCanopyHeightSource.tileKey(0.0, -60.1, 13));
   }

   @Test
   void prefetchIncludesAdjacentTileWhenCenterIsAtItsEdge() {
      int boundaryPixel = 8438 * 256;
      double longitude = -180.0 + (boundaryPixel + 0.5) / 12000.0;
      int x = (int)Math.round(EarthProjection.longitudeToBlockX(longitude, 1.0));
      assertEquals(2, TellusCanopyHeightSource.areaTileCount(x, 0, x, 0, 1.0, 1.0));
      double interiorLongitude = longitude + 50.0 / 12000.0;
      int interiorX = (int)Math.round(EarthProjection.longitudeToBlockX(interiorLongitude, 1.0));
      assertEquals(1, TellusCanopyHeightSource.areaTileCount(interiorX, 0, interiorX, 0, 1.0, 1.0));
   }

   @Test
   void pixelLookupsRespectDatasetBoundsAtNativeAndOverviewLevels() {
      assertEquals(new TellusCanopyHeightSource.TileKey(13, 0, 0),
         TellusCanopyHeightSource.tileKeyForPixel(13, 0, 0));
      assertNull(TellusCanopyHeightSource.tileKeyForPixel(13, -1, 0));
      assertNull(TellusCanopyHeightSource.tileKeyForPixel(13, 4320000, 0));
      assertNull(TellusCanopyHeightSource.tileKeyForPixel(13, 0, 1728000));
      assertEquals(new TellusCanopyHeightSource.TileKey(0, 0, 2),
         TellusCanopyHeightSource.tileKeyForPixel(0, 527, 210));
      assertNull(TellusCanopyHeightSource.tileKeyForPixel(0, 528, 0));
      assertNull(TellusCanopyHeightSource.tileKeyForPixel(0, 0, 211));
      assertEquals(new TellusCanopyHeightSource.TileKey(13, 3937, 16874),
         TellusCanopyHeightSource.tileKey(Math.nextDown(180.0), 0.0, 13));
   }

   @Test
   void coarsensLargePreviewAreasToTheirTileBudget() {
      double requestedResolution = 60.0;
      int initialTiles = TellusCanopyHeightSource.areaTileCount(-512, -512, 512, 512, 30.0, requestedResolution);

      double adjustedResolution = TellusCanopyHeightSource.resolutionForAreaTileBudget(
         -512, -512, 512, 512, 30.0, requestedResolution, 32
      );
      int adjustedTiles = TellusCanopyHeightSource.areaTileCount(-512, -512, 512, 512, 30.0, adjustedResolution);

      assertTrue(initialTiles > 32);
      assertTrue(adjustedTiles <= 32);
      assertTrue(adjustedResolution > requestedResolution);
   }
}
