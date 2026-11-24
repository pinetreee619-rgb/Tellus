package com.yucareux.tellus.worldgen;

import com.yucareux.tellus.world.data.biome.BiomeClassification;
import com.yucareux.tellus.world.data.resolve.ResolveEcoregion;
import com.yucareux.tellus.world.data.resolve.ResolveRealm;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.biome.Biome;

/**
 * Optional geographic selection of externally registered Nature's Spirit biomes.
 * Tellus retains its elevation, water, land-cover and tree placement systems.
 * No Nature's Spirit implementation or assets are bundled.
 */
final class NaturesSpiritBiomeBridge {
   private static final String PREFIX = "natures_spirit:";
   private static final List<String> IDS = List.of(
      "redwood_forest", "cypress_fields", "chaparral", "prairie",
      "marsh", "tundra", "boreal_taiga", "tropical_woods", "sparse_tropical_woods"
   );

   private final Map<String, Holder<Biome>> registered;

   private NaturesSpiritBiomeBridge(Map<String, Holder<Biome>> registered) {
      this.registered = Map.copyOf(registered);
   }

   static NaturesSpiritBiomeBridge create(HolderGetter<Biome> lookup, boolean modLoaded) {
      if (!modLoaded) return new NaturesSpiritBiomeBridge(Map.of());
      Map<String, Holder<Biome>> registered = new HashMap<>();
      for (String id : IDS) {
         // Missing or unbound keys must never enter the generator's possible biomes.
         lookup.get(BiomeClassification.toBiomeKey(PREFIX + id))
            .filter(Holder::isBound)
            .ifPresent(holder -> registered.put(id, holder));
      }
      return new NaturesSpiritBiomeBridge(registered);
   }

   boolean enabled() {
      return !this.registered.isEmpty();
   }

   List<Holder<Biome>> possibleBiomes() {
      return List.copyOf(this.registered.values());
   }

   Holder<Biome> select(Holder<Biome> fallback, int cover, String climate, ResolveEcoregion region) {
      String id = candidate(cover, climate, region);
      return id == null ? fallback : this.registered.getOrDefault(id, fallback);
   }

   /** Conservative, original mappings from Tellus's existing geographical datasets. */
   static String candidate(int cover, String climate, ResolveEcoregion region) {
      if (region == null || !region.available() || climate == null || climate.isBlank()) return null;
      String code = climate.trim().toUpperCase(Locale.ROOT);
      boolean temperate = code.startsWith("C");
      boolean tropical = code.startsWith("A");
      return switch (region.biome()) {
         case TEMPERATE_CONIFER_FORESTS -> cover == 10 && region.ecoId() == 359 && temperate
            ? "redwood_forest" : null;
         case MEDITERRANEAN_FORESTS_WOODLANDS_SCRUB ->
            (code.equals("CSA") || code.equals("CSB")) && region.realm() != ResolveRealm.AUSTRALASIA
               ? (cover == 10 ? "cypress_fields" : cover == 20 ? "chaparral" : null) : null;
         case TEMPERATE_GRASSLANDS_SAVANNAS_SHRUBLANDS ->
            cover == 30 && region.realm() == ResolveRealm.NEARCTIC
               && (temperate || code.equals("BSK") || code.startsWith("D")) ? "prairie" : null;
         case FLOODED_GRASSLANDS_SAVANNAS ->
            cover == 90 && (temperate || tropical) ? "marsh" : null;
         case TUNDRA -> code.equals("ET") && (cover == 20 || cover == 30 || cover == 100) ? "tundra" : null;
         case BOREAL_FORESTS_TAIGA ->
            cover == 10 && code.startsWith("D") && code.length() == 3
               && (code.endsWith("C") || code.endsWith("D")) ? "boreal_taiga" : null;
         case TROPICAL_MOIST_BROADLEAF_FORESTS ->
            tropical ? (cover == 10 ? "tropical_woods" : cover == 20 ? "sparse_tropical_woods" : null) : null;
         default -> null;
      };
   }
}
