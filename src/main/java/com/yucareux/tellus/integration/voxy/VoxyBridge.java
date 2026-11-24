package com.yucareux.tellus.integration.voxy;

import com.yucareux.tellus.Tellus;
import com.yucareux.tellus.platform.TellusPlatform;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.chunk.LevelChunk;

final class VoxyBridge {
   private static final String VOXY_MOD_ID = "voxy";
   private static final String VOXY_CONFIG_FILE = "voxy-config.json";
   private static final int VOXY_CHUNKS_PER_SECTION = 32;
   private static final long CONFIG_REFRESH_MS = 2000L;
   private static final Object CONFIG_LOCK = new Object();
   private static final AtomicBoolean LOGGED_CONFIG_ERROR = new AtomicBoolean(false);
   private static final VoxyRenderDistanceConfig RENDER_DISTANCE = new VoxyRenderDistanceConfig();
   private static volatile int cachedSectionRenderDistance = VoxyRenderDistanceConfig.DEFAULT_DISTANCE;
   private static volatile long nextConfigRefreshMs = 0L;
   private static final Object INGEST_LOCK = new Object();
   private static final AtomicBoolean LOGGED_INGEST_ERROR = new AtomicBoolean(false);
   
   private static volatile Method ingestMethod;
   private static volatile boolean ingestMethodResolved;

   private VoxyBridge() {
   }

   static boolean isVoxyLoaded() {
      return TellusPlatform.isModLoaded(VOXY_MOD_ID);
   }

   static int configuredChunkRadius() {
      int sectionRenderDistance = configuredSectionRenderDistance();
      return sectionRenderDistance * VOXY_CHUNKS_PER_SECTION;
   }

   private static int configuredSectionRenderDistance() {
      if (!isVoxyLoaded()) {
         return 0;
      } else {
         long now = System.currentTimeMillis();
         if (now < nextConfigRefreshMs) {
            return cachedSectionRenderDistance;
         } else {
            synchronized (CONFIG_LOCK) {
               if (now < nextConfigRefreshMs) {
                  return cachedSectionRenderDistance;
               } else {
                  refreshConfig(now);
                  return cachedSectionRenderDistance;
               }
            }
         }
      }
   }

   private static void refreshConfig(long now) {
      nextConfigRefreshMs = now + CONFIG_REFRESH_MS;
      Path path = configPath();
      try {
         RENDER_DISTANCE.refresh(path);
         LOGGED_CONFIG_ERROR.set(false);
      } catch (IOException error) {
         if (LOGGED_CONFIG_ERROR.compareAndSet(false, true)) {
            Tellus.LOGGER.warn("Failed to read Voxy config from {}", path, error);
         }
      }
      cachedSectionRenderDistance = RENDER_DISTANCE.distance();
   }

   private static Path configPath() {
      return TellusPlatform.configDir().resolve(VOXY_CONFIG_FILE);
   }

   static void tryIngestChunkAsync(MinecraftServer server, LevelChunk chunk) {
      if (isVoxyLoaded()) {
         server.execute(() -> tryIngestChunkOnServerThread(chunk));
      }
   }

   private static void tryIngestChunkOnServerThread(LevelChunk chunk) {
      Method method = resolveIngestMethod();
      if (method != null) {
         try {
            method.invoke(null, chunk);
            LOGGED_INGEST_ERROR.set(false);
         } catch (RuntimeException | ReflectiveOperationException var3) {
            if (LOGGED_INGEST_ERROR.compareAndSet(false, true)) {
               Tellus.LOGGER.warn("Failed to invoke Voxy chunk ingest bridge", var3);
            }
         }
      }
   }

   
   private static Method resolveIngestMethod() {
      if (ingestMethodResolved) {
         return ingestMethod;
      } else {
         synchronized (INGEST_LOCK) {
            if (ingestMethodResolved) {
               return ingestMethod;
            } else {
               try {
                  Class<?> serviceClass = Class.forName("me.cortex.voxy.common.world.service.VoxelIngestService");
                  ingestMethod = serviceClass.getMethod("tryAutoIngestChunk", LevelChunk.class);
               } catch (ReflectiveOperationException var3) {
                  Tellus.LOGGER.debug("Voxy ingest bridge unavailable", var3);
                  ingestMethod = null;
               }

               ingestMethodResolved = true;
               return ingestMethod;
            }
         }
      }
   }
}
