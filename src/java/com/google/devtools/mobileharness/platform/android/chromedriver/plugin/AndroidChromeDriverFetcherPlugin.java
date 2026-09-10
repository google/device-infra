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

package com.google.devtools.mobileharness.platform.android.chromedriver.plugin;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Multimap;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidChromeDriverProviderDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin.PluginType;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Mobile Harness client plugin that dynamically resolves the matching ChromeDriver binary for the
 * target Android device on the client host and attaches it to the test files before execution on
 * the remote Lab Server.
 */
@Plugin(type = PluginType.CLIENT)
public class AndroidChromeDriverFetcherPlugin {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @ParamAnnotation(
      required = false,
      help = "Optional explicit Chrome version to match when resolving ChromeDriver.")
  public static final String PARAM_CHROME_VERSION = "chrome_version";

  private static final String DECORATOR_NAME = "AndroidChromeDriverProviderDecorator";
  private static final Pattern MAJOR_VERSION_PATTERN = Pattern.compile("^(\\d+)");

  private final SystemUtil systemUtil;
  private final Path chromedriverBaseDir;

  public static boolean isEnabled(JobInfo jobInfo) {
    return jobInfo.type().getDecoratorList().contains(DECORATOR_NAME);
  }

  public AndroidChromeDriverFetcherPlugin() {
    this(new SystemUtil(), Path.of(AndroidChromeDriverProviderDecoratorSpec.CHROMEDRIVER_BASE_DIR));
  }

  @VisibleForTesting
  AndroidChromeDriverFetcherPlugin(SystemUtil systemUtil, Path chromedriverBaseDir) {
    this.systemUtil = systemUtil;
    this.chromedriverBaseDir = chromedriverBaseDir;
  }

  @Subscribe
  public void onTestStarting(TestStartingEvent event) throws MobileHarnessException {
    TestInfo testInfo = event.getTest();
    if (!testInfo.jobInfo().type().getDecoratorList().contains(DECORATOR_NAME)) {
      return;
    }

    Optional<String> majorVersionOpt = resolveTargetMajorVersion(event);
    if (majorVersionOpt.isEmpty()) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "No chrome_version dimension or param found on client; deferring ChromeDriver"
                  + " resolution to lab decorator.");
      return;
    }

    String majorVersion = majorVersionOpt.get();
    Optional<Path> driverPathOpt = findChromeDriverOnClient(majorVersion);
    if (driverPathOpt.isEmpty()) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "No local ChromeDriver binary found on client for major version %s; deferring to lab"
                  + " decorator.",
              majorVersion);
      return;
    }

    String resolvedPath = driverPathOpt.get().toAbsolutePath().toString();
    testInfo
        .files()
        .add(AndroidChromeDriverProviderDecoratorSpec.FILE_CHROMEDRIVER_BINARIES, resolvedPath);
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "Attached ChromeDriver binary for major version %s to test files: %s",
            majorVersion, resolvedPath);
  }

  // Resolves the target Chrome or WebView major version by checking in order:
  // 1. Explicit job parameter (chrome_version).
  // 2. Remote lab device allocation dimensions (chrome_version).
  private Optional<String> resolveTargetMajorVersion(TestStartingEvent event) {
    Optional<String> version = resolveMajorVersionFromJobParam(event.getTest());
    if (version.isPresent()) {
      return version;
    }
    return resolveMajorVersionFromAllocation(event);
  }

  // Approach 1: Checks if the test configuration explicitly specifies a 'chrome_version' job
  // parameter. This allows test authors or CI suites to pin or override a specific ChromeDriver
  // version.
  private Optional<String> resolveMajorVersionFromJobParam(TestInfo testInfo) {
    String paramVersion = testInfo.jobInfo().params().get(PARAM_CHROME_VERSION);
    if (paramVersion != null && !paramVersion.isEmpty()) {
      return extractMajorVersion(paramVersion);
    }
    return Optional.empty();
  }

  // Approach 2: Checks the 'chrome_version' dimension from the device allocation metadata.
  // When running against a remote Lab Server (OmniLab/Guitar), the physical device is not directly
  // reachable via local ADB on the client host. However, the Lab Server periodically probes
  // connected devices and publishes their detected Chrome version in the allocation dimensions.
  private Optional<String> resolveMajorVersionFromAllocation(TestStartingEvent event) {
    for (Multimap<String, String> deviceDimensions :
        event.getAllocation().getAllDevices().values()) {
      Collection<String> chromeVersions =
          deviceDimensions.get(Dimension.Name.CHROME_VERSION.lowerCaseName());
      if (!chromeVersions.isEmpty()) {
        String rawVersion = chromeVersions.iterator().next();
        Optional<String> extracted = extractMajorVersion(rawVersion);
        if (extracted.isPresent()) {
          return extracted;
        }
      }
    }
    return Optional.empty();
  }

  private Optional<String> extractMajorVersion(String versionString) {
    Matcher matcher = MAJOR_VERSION_PATTERN.matcher(versionString.trim());
    if (matcher.find()) {
      return Optional.of(matcher.group(1));
    }
    return Optional.empty();
  }

  private Optional<Path> findChromeDriverOnClient(String majorVersion) {
    String platformSubdir = systemUtil.isOnMac() ? "macos" : "linux";
    Path platformDir = chromedriverBaseDir.resolve(platformSubdir);
    if (!Files.exists(platformDir)) {
      platformDir =
          Path.of(
              AndroidChromeDriverProviderDecoratorSpec.HEAD_DEPOT_CHROMEDRIVER_BASE_DIR,
              platformSubdir);
    }
    if (!Files.exists(platformDir)) {
      return Optional.empty();
    }

    String majorPrefix = "v" + majorVersion + "_";
    try (Stream<Path> stream = Files.list(platformDir)) {
      return stream
          .filter(Files::isDirectory)
          .filter(p -> p.getFileName().toString().startsWith(majorPrefix))
          .map(p -> p.resolve(AndroidChromeDriverProviderDecoratorSpec.CHROMEDRIVER_BINARY_NAME))
          .filter(Files::exists)
          .findFirst();
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to search ChromeDriver directory on client: %s", platformDir);
    }
    return Optional.empty();
  }
}
