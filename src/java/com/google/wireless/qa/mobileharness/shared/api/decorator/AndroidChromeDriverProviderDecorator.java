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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryException;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryStrategy;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryingCallable;
import com.google.devtools.mobileharness.shared.util.port.PortProber;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.TeardownContext;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidChromeDriverProviderDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Reusable MobileHarness decorator for dynamic ChromeDriver resolution and process lifecycle on
 * Android SUT.
 *
 * <p>For the overall design and architecture, see <a
 * href="http://go/waddi-omnilab#heading=h.wv3r8ewb03pi">Integrating Web Testing Bridge into OmniLab
 * & FTL</a>.
 */
@DecoratorAnnotation(
    help = "Decorator to resolve, start, and tear down compatible ChromeDriver dynamically.")
public class AndroidChromeDriverProviderDecorator extends LifecycleDecorator
    implements AndroidChromeDriverProviderDecoratorSpec {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String LOCALHOST_IP = "127.0.0.1";
  private static final String PROPERTY_SELENIUM_ADDRESS = "SELENIUM_ADDRESS";
  private static final String PROPERTY_SELENIUM_ADDRESS_LEGACY = "seleniumAddress";
  private static final String ENV_VAR_PREFIX = "env_var_";

  private final AndroidPackageManagerUtil packageManagerUtil;
  private final SystemUtil systemUtil;
  private final Path chromedriverBaseDir;
  private final CommandExecutor commandExecutor;
  @Nullable private CommandProcess chromedriverProcess;

  @Inject
  AndroidChromeDriverProviderDecorator(Driver decoratedDriver, TestInfo testInfo, Device device) {
    this(
        decoratedDriver,
        testInfo,
        device,
        new AndroidPackageManagerUtil(),
        new SystemUtil(),
        Path.of(CHROMEDRIVER_BASE_DIR),
        new CommandExecutor());
  }

  @VisibleForTesting
  AndroidChromeDriverProviderDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      Device device,
      AndroidPackageManagerUtil packageManagerUtil) {
    this(
        decoratedDriver,
        testInfo,
        device,
        packageManagerUtil,
        new SystemUtil(),
        Path.of(CHROMEDRIVER_BASE_DIR),
        new CommandExecutor());
  }

  @VisibleForTesting
  AndroidChromeDriverProviderDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      Device device,
      AndroidPackageManagerUtil packageManagerUtil,
      SystemUtil systemUtil) {
    this(
        decoratedDriver,
        testInfo,
        device,
        packageManagerUtil,
        systemUtil,
        Path.of(CHROMEDRIVER_BASE_DIR),
        new CommandExecutor());
  }

  @VisibleForTesting
  AndroidChromeDriverProviderDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      Device device,
      AndroidPackageManagerUtil packageManagerUtil,
      SystemUtil systemUtil,
      Path chromedriverBaseDir) {
    this(
        decoratedDriver,
        testInfo,
        device,
        packageManagerUtil,
        systemUtil,
        chromedriverBaseDir,
        new CommandExecutor());
  }

  @VisibleForTesting
  AndroidChromeDriverProviderDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      Device device,
      AndroidPackageManagerUtil packageManagerUtil,
      SystemUtil systemUtil,
      Path chromedriverBaseDir,
      CommandExecutor commandExecutor) {
    super(decoratedDriver, testInfo);
    this.packageManagerUtil = packageManagerUtil;
    this.systemUtil = systemUtil;
    this.chromedriverBaseDir = chromedriverBaseDir;
    this.commandExecutor = commandExecutor;
  }

  @Override
  protected SetupResult setUp(SetupContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    String deviceId = getDevice().getDeviceId();

    String resolvedPath = null;
    if (testInfo.jobInfo().files().isTagNotEmpty(FILE_CHROMEDRIVER)) {
      resolvedPath = testInfo.jobInfo().files().getSingle(FILE_CHROMEDRIVER);
    }
    if (Strings.isNullOrEmpty(resolvedPath)) {
      resolvedPath = testInfo.jobInfo().params().get(PARAM_CHROMEDRIVER_PATH, null);
    }

    if (Strings.isNullOrEmpty(resolvedPath)) {
      String rawTargetType =
          testInfo.jobInfo().params().get(PARAM_TARGET_TYPE, TARGET_TYPE_BROWSER);
      String targetType =
          (rawTargetType != null ? rawTargetType : TARGET_TYPE_BROWSER)
              .trim()
              .toLowerCase(Locale.US);
      String packageName;
      if (targetType.equals(TARGET_TYPE_WEBVIEW)) {
        String webviewPkg = testInfo.jobInfo().params().get(PARAM_WEBVIEW_PACKAGE_NAME, null);
        if (webviewPkg != null && !webviewPkg.isEmpty()) {
          packageName = webviewPkg;
        } else {
          try {
            String wv = packageManagerUtil.getAppVersionName(deviceId, DEFAULT_WEBVIEW_PACKAGE);
            packageName =
                (wv != null && !wv.isEmpty()) ? DEFAULT_WEBVIEW_PACKAGE : DEFAULT_CHROME_PACKAGE;
          } catch (MobileHarnessException e) {
            packageName = DEFAULT_CHROME_PACKAGE;
          }
        }
      } else {
        String defaultPkg =
            testInfo.jobInfo().params().get(PARAM_PACKAGE_NAME, DEFAULT_CHROME_PACKAGE);
        if (defaultPkg == null) {
          defaultPkg = DEFAULT_CHROME_PACKAGE;
        }
        packageName = testInfo.jobInfo().params().get(PARAM_BROWSER_PACKAGE_NAME, defaultPkg);
        if (packageName == null) {
          packageName = defaultPkg;
        }
      }

      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Detecting Chrome/WebView version name on device using package %s...", packageName);
      String versionName = packageManagerUtil.getAppVersionName(deviceId, packageName);
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Detected browser/WebView version: %s", versionName);

      List<String> versionParts = Splitter.on('.').splitToList(versionName);
      if (versionParts.isEmpty()) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GET_VERSION_INFO_ERROR,
            "Failed to parse Chrome/WebView version: " + versionName);
      }
      String majorVersion = versionParts.get(0);
      resolvedPath = resolveChromeDriver(majorVersion, testInfo);
    }

    testInfo.log().atInfo().alsoTo(logger).log("Staging ChromeDriver binary: %s", resolvedPath);
    testInfo.jobInfo().files().add(FILE_CHROMEDRIVER, resolvedPath);

    int chromedriverPort = startChromeDriver(resolvedPath, testInfo);
    if (chromedriverPort > 0) {
      String seleniumUrl = "http://" + LOCALHOST_IP + ":" + chromedriverPort;
      testInfo.properties().add(PROPERTY_SELENIUM_ADDRESS, seleniumUrl);
      testInfo.properties().add(ENV_VAR_PREFIX + PROPERTY_SELENIUM_ADDRESS, seleniumUrl);
      testInfo.properties().add(PROPERTY_SELENIUM_ADDRESS_LEGACY, seleniumUrl);
    }
    return SetupResult.continueDecorated();
  }

  @Override
  protected void tearDown(TeardownContext context)
      throws MobileHarnessException, InterruptedException {
    if (this.chromedriverProcess != null) {
      context.testInfo().log().atInfo().alsoTo(logger).log("Tearing down ChromeDriver...");
      this.chromedriverProcess.kill();
      this.chromedriverProcess = null;
    }
  }

  private int startChromeDriver(String chromedriverPath, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    int chromedriverPort = 0;
    try {
      chromedriverPort = PortProber.pickUnusedPort();
    } catch (IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_CDP_PORT_ALLOCATION_ERROR,
          "Failed to pick an unused dynamic port for ChromeDriver.",
          e);
    }
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Starting ChromeDriver on port %d...", chromedriverPort);
    try {
      this.chromedriverProcess =
          commandExecutor.start(
              Command.of(chromedriverPath, "--port=" + chromedriverPort)
                  .redirectStderr(true)
                  .onStdout(
                      LineCallback.does(
                          line ->
                              testInfo
                                  .log()
                                  .atInfo()
                                  .alsoTo(logger)
                                  .log("[ChromeDriver] %s", line))));
    } catch (CommandException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_CDP_CHROMEDRIVER_ERROR, "Failed to start ChromeDriver.", e);
    }
    try {
      waitForPort(chromedriverPort, Duration.ofSeconds(10));
    } catch (MobileHarnessException | InterruptedException e) {
      if (this.chromedriverProcess != null) {
        this.chromedriverProcess.kill();
        this.chromedriverProcess = null;
      }
      if (e instanceof InterruptedException interruptedException) {
        throw interruptedException;
      }
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_CDP_CHROMEDRIVER_ERROR,
          "ChromeDriver failed to bind to port " + chromedriverPort,
          e);
    }
    return chromedriverPort;
  }

  @VisibleForTesting
  void waitForPort(int port, Duration timeout) throws MobileHarnessException, InterruptedException {
    try {
      RetryingCallable.newBuilder(
              () -> {
                if (chromedriverProcess != null && !chromedriverProcess.isAlive()) {
                  throw new MobileHarnessException(
                      AndroidErrorId.ANDROID_CDP_CHROMEDRIVER_ERROR,
                      "ChromeDriver process exited unexpectedly.");
                }
                try {
                  InetAddress[] addresses = InetAddress.getAllByName(LOCALHOST_IP);
                  if (addresses.length == 0) {
                    throw new MobileHarnessException(
                        AndroidErrorId.ANDROID_CDP_CHROMEDRIVER_ERROR,
                        "No address found for " + LOCALHOST_IP);
                  }
                  try (Socket socket = new Socket(addresses[0], port)) {
                    return true;
                  }
                } catch (IOException e) {
                  throw new MobileHarnessException(
                      AndroidErrorId.ANDROID_CDP_CHROMEDRIVER_ERROR, "Port is not open yet.", e);
                }
              },
              RetryStrategy.uniformDelay(
                  Duration.ofMillis(100), Math.max(1, (int) (timeout.toMillis() / 100))))
          .setPredicate(e -> chromedriverProcess == null || chromedriverProcess.isAlive())
          .build()
          .call();
    } catch (RetryException e) {
      if (e.getCause() instanceof InterruptedException interruptedException) {
        throw interruptedException;
      }
      if (Thread.interrupted()) {
        throw new InterruptedException(
            String.format("Interrupted while waiting for port %d to open.", port));
      }
      if (chromedriverProcess != null && !chromedriverProcess.isAlive()) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_CDP_CHROMEDRIVER_ERROR,
            String.format("ChromeDriver process exited unexpectedly before port %d opened.", port),
            e.getCause());
      }
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_CDP_CHROMEDRIVER_ERROR,
          String.format("Timed out waiting for port %d to open.", port),
          e.getCause());
    }
  }

  private String resolveChromeDriver(String majorVersion, TestInfo testInfo)
      throws MobileHarnessException {
    String majorPrefix = "v" + majorVersion + "_";

    // First check test-scoped files attached by Client Plugin (AndroidChromeDriverFetcherPlugin)
    ImmutableSet<String> testCandidates = testInfo.files().get(FILE_CHROMEDRIVER_BINARIES);
    for (String candidatePathStr : testCandidates) {
      Path candidatePath = Path.of(candidatePathStr);
      if (Files.exists(candidatePath) && candidatePathStr.contains(majorPrefix)) {
        return candidatePath.toAbsolutePath().toString();
      }
    }

    // Next check if candidate ChromeDriver binaries were staged via job files
    for (String candidatePathStr : testInfo.jobInfo().files().get(FILE_CHROMEDRIVER_BINARIES)) {
      if (candidatePathStr.contains(majorPrefix)) {
        Path candidatePath = Path.of(candidatePathStr);
        if (Files.exists(candidatePath)) {
          return candidatePath.toAbsolutePath().toString();
        }
      }
    }

    // Fall back to runfiles depot directory
    String platformSubdir = getHostPlatformSubdir();
    Path platformDir = chromedriverBaseDir.resolve(platformSubdir);
    if (!Files.exists(platformDir)) {
      platformDir = Path.of(HEAD_DEPOT_CHROMEDRIVER_BASE_DIR, platformSubdir);
    }
    if (!Files.exists(platformDir)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_PM_PATH_NO_PACKAGE_FOUND,
          "ChromeDriver directory not found in runfiles: " + platformDir);
    }

    try (Stream<Path> stream = Files.list(platformDir)) {
      Optional<Path> match =
          stream
              .filter(Files::isDirectory)
              .filter(p -> p.getFileName().toString().startsWith(majorPrefix))
              .map(p -> p.resolve(CHROMEDRIVER_BINARY_NAME))
              .filter(Files::exists)
              .findFirst();
      if (match.isPresent()) {
        return match.get().toAbsolutePath().toString();
      }
    } catch (IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_PM_PATH_NO_PACKAGE_FOUND,
          "Error searching ChromeDriver directory",
          e);
    }

    throw new MobileHarnessException(
        AndroidErrorId.ANDROID_PKG_MNGR_UTIL_PM_PATH_NO_PACKAGE_FOUND,
        "No compatible ChromeDriver found in depot for Chrome major version: " + majorVersion);
  }

  private String getHostPlatformSubdir() {
    return systemUtil.isOnMac() ? "macos" : "linux";
  }
}
