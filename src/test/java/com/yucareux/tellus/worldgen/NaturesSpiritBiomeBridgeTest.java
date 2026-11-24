package com.yucareux.tellus.worldgen;

import com.yucareux.tellus.world.data.resolve.ResolveBiome;
import com.yucareux.tellus.world.data.resolve.ResolveEcoregion;
import com.yucareux.tellus.world.data.resolve.ResolveRealm;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturesSpiritBiomeBridgeTest {
   @Test
   void absentModDoesNotQueryOptionalRegistryKeys() {
      AtomicInteger queries = new AtomicInteger();
      var bridge = NaturesSpiritBiomeBridge.create(emptyLookup(queries), false);
      assertFalse(bridge.enabled());
      assertTrue(bridge.possibleBiomes().isEmpty());
      assertEquals(0, queries.get());
   }

   @Test
   void missingOptionalKeysLeaveOriginalClassificationUntouched() {
      AtomicInteger queries = new AtomicInteger();
      var bridge = NaturesSpiritBiomeBridge.create(emptyLookup(queries), true);
      assertFalse(bridge.enabled());
      assertTrue(bridge.possibleBiomes().isEmpty());
      assertEquals(9, queries.get());
      assertNull(bridge.select(null, 30, "Cfb",
         region(1, ResolveBiome.TEMPERATE_GRASSLANDS_SAVANNAS_SHRUBLANDS, ResolveRealm.NEARCTIC)));
   }

   @Test
   void mapsRegionalBiomesOnlyWhenClimateAndLandCoverAgree() {
      var redwood = region(359, ResolveBiome.TEMPERATE_CONIFER_FORESTS, ResolveRealm.NEARCTIC);
      assertEquals("redwood_forest", NaturesSpiritBiomeBridge.candidate(10, "Csb", redwood));
      assertNull(NaturesSpiritBiomeBridge.candidate(40, "Csb", redwood));
      assertNull(NaturesSpiritBiomeBridge.candidate(10, "Dfc", redwood));
      var mediterranean = region(2, ResolveBiome.MEDITERRANEAN_FORESTS_WOODLANDS_SCRUB, ResolveRealm.PALEARCTIC);
      assertEquals("chaparral", NaturesSpiritBiomeBridge.candidate(20, "Csa", mediterranean));
      assertNull(NaturesSpiritBiomeBridge.candidate(20, "ET", mediterranean));
      var tropical = region(3, ResolveBiome.TROPICAL_MOIST_BROADLEAF_FORESTS, ResolveRealm.INDOMALAYAN);
      assertEquals("tropical_woods", NaturesSpiritBiomeBridge.candidate(10, "Am", tropical));
      assertNull(NaturesSpiritBiomeBridge.candidate(10, "Dfc", tropical));
      assertNull(NaturesSpiritBiomeBridge.candidate(95, "Am", tropical));
      assertNull(NaturesSpiritBiomeBridge.candidate(10, "Am", ResolveEcoregion.UNKNOWN));
   }

   private static ResolveEcoregion region(int id, ResolveBiome biome, ResolveRealm realm) {
      return new ResolveEcoregion(id, "Test", biome, biome.displayName(), realm, realm.name(), "");
   }

   @SuppressWarnings("unchecked")
   private static HolderGetter<Biome> emptyLookup(AtomicInteger queries) {
      return (HolderGetter<Biome>)Proxy.newProxyInstance(
         NaturesSpiritBiomeBridgeTest.class.getClassLoader(), new Class<?>[]{HolderGetter.class},
         (proxy, method, args) -> {
            if (method.getName().equals("get")) {
               queries.incrementAndGet();
               return Optional.empty();
            }
            throw new UnsupportedOperationException(method.toString());
         });
   }
}
