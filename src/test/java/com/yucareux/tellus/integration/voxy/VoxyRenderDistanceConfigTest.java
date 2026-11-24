package com.yucareux.tellus.integration.voxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VoxyRenderDistanceConfigTest {
   @TempDir
   Path directory;

   @Test
   void retriesFailedParseEvenWhenTheRepairedFileHasTheSameMetadata() throws IOException {
      Path path = this.directory.resolve("voxy-config.json");
      VoxyRenderDistanceConfig config = new VoxyRenderDistanceConfig();
      write(path, "{\"section_render_distance\":32}", 1_000_000L);
      config.refresh(path);
      assertEquals(32, config.distance());

      write(path, "{\"section_render_distance\":xx}", 2_000_000L);
      assertThrows(IOException.class, () -> config.refresh(path));
      assertEquals(32, config.distance());
      assertThrows(IOException.class, () -> config.refresh(path));

      write(path, "{\"section_render_distance\":48}", 2_000_000L);
      config.refresh(path);
      assertEquals(48, config.distance());
   }

   @Test
   void missingConfigResetsDistanceAndAllowsRecreation() throws IOException {
      Path path = this.directory.resolve("voxy-config.json");
      VoxyRenderDistanceConfig config = new VoxyRenderDistanceConfig();
      config.refresh(path);
      assertEquals(16, config.distance());
      write(path, "{\"section_render_distance\":99}", 1_000_000L);
      config.refresh(path);
      assertEquals(64, config.distance());
      Files.delete(path);
      config.refresh(path);
      assertEquals(16, config.distance());
      write(path, "{\"section_render_distance\":-1}", 1_000_000L);
      config.refresh(path);
      assertEquals(2, config.distance());
   }

   @Test
   void changingConfigPathDoesNotReuseAnotherWorldsDistance() throws IOException {
      Path first = this.directory.resolve("first.json");
      Path second = this.directory.resolve("second.json");
      VoxyRenderDistanceConfig config = new VoxyRenderDistanceConfig();
      write(first, "{\"section_render_distance\":48}", 1_000_000L);
      write(second, "{\"section_render_distance\":24}", 1_000_000L);
      config.refresh(first);
      assertEquals(48, config.distance());
      config.refresh(second);
      assertEquals(24, config.distance());
      write(second, "{}", 2_000_000L);
      config.refresh(second);
      assertEquals(16, config.distance());
   }

   private static void write(Path path, String content, long timestamp) throws IOException {
      Files.writeString(path, content);
      Files.setLastModifiedTime(path, FileTime.fromMillis(timestamp));
   }
}
