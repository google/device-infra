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

package com.google.devtools.mobileharness.platform.testbed.config;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.Files;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.testbed.config.json.YamlTestbedLoader;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.device.MiscTestbedSubDevice;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Helper class for loading testbed configs. */
public class GlobalTestbedLoader implements TestbedLoader {

  private static final ImmutableSet<String> VALID_FILE_EXTENSIONS =
      ImmutableSet.of("json", "yaml", "yml");

  private static final int LOG_TIME_LIMIT_SEC = 60;

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Set<String> missingFiles = new HashSet<>();

  private final LocalFileUtil localFileUtil;
  private final List<String> localTestbedPaths;

  /** Gets the singleton instance. */
  public static final GlobalTestbedLoader getInstance() {
    return SingletonHolder.SINGLETON;
  }

  /** Checks to see if the config loader will ever load any testbed configs. */
  public boolean isActive() {
    return !localTestbedPaths.isEmpty();
  }

  /**
   * Loads all testbed configs.
   *
   * @return All testbed configs merged into a single map of testbed name -> testbed config
   */
  @Override
  public Map<String, TestbedConfig> getTestbedConfigs() {
    Map<String, TestbedConfig> configContainers = new HashMap<>();

    for (String path : localTestbedPaths) {
      Map<String, TestbedConfig> pathConfig = ImmutableMap.of();
      try {
        if (localFileUtil.isDirExist(path)) {
          pathConfig = loadDirectory(path);
        } else {
          pathConfig = loadSingleTestbed(path);
        }
      } catch (MobileHarnessException e) {
        logger.atWarning().atMostEvery(LOG_TIME_LIMIT_SEC, SECONDS).withCause(e).log(
            "Error loading testbed config: %s", path);
      }
      configContainers.putAll(pathConfig);
    }
    return filterTestbedConfigs(configContainers);
  }

  /** Singleton holder for lazy initialization. */
  private static class SingletonHolder {
    private static final GlobalTestbedLoader SINGLETON = new GlobalTestbedLoader();
  }

  private GlobalTestbedLoader() {
    // GlobalTestbedLoader is a singleton.
    this(new LocalFileUtil(), Flags.instance().testbedConfigPaths.get());
  }

  @VisibleForTesting
  GlobalTestbedLoader(LocalFileUtil localFileUtil, List<String> localTestbedPaths) {
    this.localFileUtil = localFileUtil;
    this.localTestbedPaths = localTestbedPaths;
  }

  private ImmutableMap<String, TestbedConfig> loadDirectory(String dir)
      throws MobileHarnessException {
    if (localFileUtil.isDirExist(dir)) {
      Map<String, TestbedConfig> allConfigs = new HashMap<>();
      List<File> files =
          localFileUtil.listFiles(
              dir,
              /* recursively= */ true,
              pathname ->
                  VALID_FILE_EXTENSIONS.contains(
                      Files.getFileExtension(pathname.getAbsolutePath())));
      for (File file : files) {
        Map<String, TestbedConfig> configMap = loadSingleTestbed(file.getAbsolutePath());
        allConfigs.putAll(configMap);
      }
      return ImmutableMap.copyOf(allConfigs);
    } else {
      logMissingFile(dir);
      return ImmutableMap.of();
    }
  }

  private Map<String, TestbedConfig> loadSingleTestbed(String yamlFile)
      throws MobileHarnessException {
    if (localFileUtil.isFileExist(yamlFile)) {
      return YamlTestbedLoader.fromFilename(yamlFile).getTestbedConfigs();
    } else {
      logMissingFile(yamlFile);
      return ImmutableMap.of();
    }
  }

  private synchronized void logMissingFile(String file) {
    if (!missingFiles.contains(file)) {
      logger.atWarning().atMostEvery(LOG_TIME_LIMIT_SEC, SECONDS).log(
          "Missing testbed location %s", file);
      missingFiles.add(file);
    }
  }

  /**
   * Filters out testbeds with duplicated sub devices.
   *
   * @return A subset of the input map with filtered out testbeds.
   */
  private static ImmutableMap<String, TestbedConfig> filterTestbedConfigs(
      Map<String, TestbedConfig> testbedConfigs) {
    ImmutableSetMultimap.Builder<SubDeviceKey, String> deviceLookupBuilder =
        new ImmutableSetMultimap.Builder<>();
    testbedConfigs
        .values()
        .forEach(
            config ->
                config
                    .getDevices()
                    .keySet()
                    .forEach(
                        subDeviceKey -> deviceLookupBuilder.put(subDeviceKey, config.getName())));
    ImmutableSetMultimap<SubDeviceKey, String> deviceToTestbedLookup = deviceLookupBuilder.build();
    return testbedConfigs.entrySet().stream()
        .filter(
            entry ->
                entry.getValue().getDevices().keySet().stream()
                    .allMatch(
                        subDeviceKey ->
                            checkSubDeviceKey(
                                subDeviceKey, deviceToTestbedLookup, entry.getValue().getName())))
        .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static boolean checkSubDeviceKey(
      SubDeviceKey subDeviceKey,
      Multimap<SubDeviceKey, String> deviceToTestbedLookup,
      String testbedName) {
    Collection<String> testbeds = deviceToTestbedLookup.get(subDeviceKey);
    if (isSubDeviceMiscType(subDeviceKey.deviceType()) || testbeds.size() == 1) {
      return true;
    }
    logger.atWarning().atMostEvery(LOG_TIME_LIMIT_SEC, SECONDS).log(
        "Testbed (%s) contains a sub device (%s) that is duplicated in other testbeds (%s). This"
            + " testbed will be ignored.",
        testbedName, subDeviceKey.deviceId(), testbeds);
    return false;
  }

  private static boolean isSubDeviceMiscType(Class<? extends Device> clazz) {
    return MiscTestbedSubDevice.class.isAssignableFrom(clazz);
  }
}
