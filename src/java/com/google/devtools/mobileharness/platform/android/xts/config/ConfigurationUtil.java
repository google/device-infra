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

package com.google.devtools.mobileharness.platform.android.xts.config;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.File;
import java.io.FileFilter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** Utility functions to handle configuration files. */
final class ConfigurationUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String[] CONFIG_FILE_EXTENSIONS = {".config", ".xml"};

  private final LocalFileUtil localFileUtil;

  public ConfigurationUtil() {
    this.localFileUtil = new LocalFileUtil();
  }

  /**
   * Gets the test config file paths from given directories.
   *
   * @param subPath subdirectory to look for configurations. If null, only config files from {@code
   *     dirs} will be searched.
   * @param dirs a list of {@link File} of extra directories to search for test configurations
   */
  public ImmutableSet<String> getConfigPathsFromDirs(@Nullable String subPath, List<File> dirs) {
    return getConfigFilesFromDirs(subPath, dirs).stream()
        .map(File::getAbsolutePath)
        .collect(toImmutableSet());
  }

  /**
   * Gets the test config files from given directories.
   *
   * @param subPath subdirectory to look for configurations
   * @param dirs A list of {@link File} of extra directories to search for test configs
   * @return the set of {@link File} that were found.
   */
  public Set<File> getConfigFilesFromDirs(@Nullable String subPath, List<File> dirs) {
    FileFilter configFileFilter =
        (file) -> {
          return stream(CONFIG_FILE_EXTENSIONS)
              .anyMatch(extension -> file.getName().endsWith(extension));
        };

    return getConfigFilesFromDirs(subPath, dirs, configFileFilter);
  }

  /**
   * Search a particular pattern of in the given directories.
   *
   * @param subPath subdirectory to look for configurations
   * @param dirs A list of {@link File} of extra directories to search for test configs
   * @param configFileFilter filters out configuration files
   * @return the set of {@link File} that were found
   */
  public Set<File> getConfigFilesFromDirs(
      @Nullable String subPath, List<File> dirs, FileFilter configFileFilter) {
    Set<File> configNames = new LinkedHashSet<>();
    for (File dir : dirs) {
      File configDir = new File(dir.getAbsolutePath());
      if (subPath != null) {
        configDir = new File(dir, subPath);
      }
      if (!configDir.isDirectory()) {
        logger.atWarning().log(
            "%s doesn't exist or is not a directory.", configDir.getAbsolutePath());
        continue;
      }
      try {
        configNames.addAll(
            localFileUtil.listFiles(
                configDir.getAbsolutePath(), /* recursively= */ false, configFileFilter));
      } catch (MobileHarnessException e) {
        logger.atSevere().withCause(e).log(
            "Failed to get test config files from directory %s", configDir.getAbsolutePath());
      }
    }
    // From a same tests dir we only expect a single instance of each names, so we dedup the files
    // if that happens.
    return dedupFiles(configNames);
  }

  /** Filters out the configuration files and depups the files having the same names. */
  private Set<File> dedupFiles(Set<File> originalConfigSet) {
    Map<String, File> configNameToFileMap = new LinkedHashMap<>();
    for (File file : originalConfigSet) {
      try {
        /*
         */
        if (!localFileUtil.readFile(file.getAbsolutePath()).contains("<configuration")) {
          logger.atInfo().log("%s doesn't look like a test configuration.", file.getAbsolutePath());
          continue;
        }
      } catch (MobileHarnessException e) {
        logger.atSevere().withCause(e).log(
            "Failed to check file content %s", file.getAbsolutePath());
        continue;
      }
      // Always keep the first found
      configNameToFileMap.putIfAbsent(file.getName(), file);
    }
    return new LinkedHashSet<>(configNameToFileMap.values());
  }
}
