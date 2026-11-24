# Experimental Nature's Spirit biome bridge

This is an optional registry-based integration, not a port or bundled copy of
Nature's Spirit. It is disabled when the external mod is absent and falls back
to Tellus's existing biome selection for unavailable registry entries.

The bridge matches a small set of Nature's Spirit biomes to RESOLVE ecoregions,
Köppen climate and ESA land-cover classes. It does not change the Earth elevation,
water or procedural tree generation systems. It does not include external
textures, blocks, or source code.

The upstream 26.1.2 branch is incompatible with Tellus's Minecraft 26.2 and
26.3 targets as of November 24, 2026. Do not install that build into either
target or bypass its loader version restrictions. This bridge needs an upstream
compatible release before live testing is possible. Preview colors, biome
surface palettes, native tree features, and LOD appearance are not integrated
by this change. Existing worlds should be backed up before enabling any new
biome mod.

Upstream: https://github.com/Team-Hibiscus/NaturesSpirit
