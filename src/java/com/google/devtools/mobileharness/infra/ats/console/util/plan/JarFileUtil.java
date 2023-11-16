/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.mobileharness.infra.ats.console.util.plan;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import javax.inject.Inject;

/** Utility to get the entries in the JAR file. */
public class JarFileUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** A filter for entry paths in a JAR file. */
  public static interface EntryFilter {
    /**
     * Tests whether or not the specified abstract pathname should be included in a entry list.
     *
     * @param pathName the relative path of the entry in the JAR file
     */
    boolean accept(String pathName);

    /**
     * An optional converter for a entry path names.
     *
     * @param pathName the relative path of the entry in the JAR file, in format
     *     "foo/path/file.ext".
     * @return the pathName converted into context specific format
     */
    String transform(String pathName);
  }

  @Inject
  JarFileUtil() {}

  /**
   * Gets the names of all entries contained in the given jar files, that match the given {@code
   * filter}.
   */
  public Map<String, Path> getEntriesFromJars(List<Path> jars, EntryFilter filter) {
    Map<String, Path> entryNames = new HashMap<>();
    for (Path jar : jars) {
      try {
        entryNames.putAll(getEntriesFromJar(jar, filter));
      } catch (IOException e) {
        logger.atWarning().log(
            "Failed to get entries from jar %s: %s", jar, MoreThrowables.shortDebugString(e, 0));
      }
    }
    return ImmutableMap.copyOf(entryNames);
  }

  /**
   * Gets the names of all entries contained in the given jar file, that match the given {@code
   * filter}.
   */
  private ImmutableMap<String, Path> getEntriesFromJar(Path jar, EntryFilter filter)
      throws IOException {
    JarFile jarFile = new JarFile(jar.toFile());
    ImmutableMap.Builder<String, Path> entryNames = ImmutableMap.builder();
    for (Enumeration<? extends ZipEntry> e = jarFile.entries(); e.hasMoreElements(); ) {
      String entryName = e.nextElement().getName();
      if (filter.accept(entryName)) {
        entryNames.put(filter.transform(entryName), jar);
      }
      entryName = null;
    }
    jarFile.close();
    return entryNames.buildOrThrow();
  }

  /** Gets the zip entry input stream from the {@code jar} file. */
  public Optional<InputStream> getZipEntryInputStream(Path jar, String entryName) {
    JarFile jarFile;
    try {
      jarFile = new JarFile(jar.toFile());
    } catch (IOException e) {
      logger.atWarning().log(
          "Failed to open jar file %s: %s", jar, MoreThrowables.shortDebugString(e, 0));
      return Optional.empty();
    }

    Optional<? extends ZipEntry> entry =
        jarFile.stream().filter(ze -> ze.getName().equals(entryName)).findFirst();
    if (entry.isEmpty()) {
      return Optional.empty();
    }

    try {
      return Optional.of(jarFile.getInputStream(entry.get()));
    } catch (IOException e) {
      logger.atWarning().log(
          "Failed to get input stream for entry %s in jar file %s: %s",
          entry, jar, MoreThrowables.shortDebugString(e, 0));
    }
    return Optional.empty();
  }
}
