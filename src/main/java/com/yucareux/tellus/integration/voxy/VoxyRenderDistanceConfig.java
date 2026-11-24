package com.yucareux.tellus.integration.voxy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

/** Last successfully loaded render distance; callers serialize refreshes. */
final class VoxyRenderDistanceConfig {
   static final int DEFAULT_DISTANCE = 16;
   private static final int MIN_DISTANCE = 2;
   private static final int MAX_DISTANCE = 64;
   private Path path;
   private FileTime modified;
   private long size;
   private Object fileKey;
   private int distance = DEFAULT_DISTANCE;

   int distance() {
      return this.distance;
   }

   void refresh(Path requestedPath) throws IOException {
      if (!requestedPath.equals(this.path)) {
         this.path = requestedPath;
         this.reset();
      }
      BasicFileAttributes attributes;
      try {
         attributes = Files.readAttributes(requestedPath, BasicFileAttributes.class);
      } catch (NoSuchFileException absent) {
         this.reset();
         return;
      }
      if (attributes.lastModifiedTime().equals(this.modified)
         && attributes.size() == this.size
         && java.util.Objects.equals(attributes.fileKey(), this.fileKey)) {
         return;
      }

      // Do not commit metadata or replace the last valid value until parsing succeeds.
      int parsed = read(requestedPath);
      this.distance = parsed;
      this.modified = attributes.lastModifiedTime();
      this.size = attributes.size();
      this.fileKey = attributes.fileKey();
   }

   private void reset() {
      this.modified = null;
      this.size = 0L;
      this.fileKey = null;
      this.distance = DEFAULT_DISTANCE;
   }

   private static int read(Path path) throws IOException {
      try (Reader reader = Files.newBufferedReader(path)) {
         JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
         if (!root.has("section_render_distance")) {
            return DEFAULT_DISTANCE;
         }
         return Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, root.get("section_render_distance").getAsInt()));
      } catch (RuntimeException invalid) {
         throw new IOException("Invalid Voxy config structure", invalid);
      }
   }
}
