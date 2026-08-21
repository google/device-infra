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
import com.google.common.flogger.FluentLogger;
import com.google.common.net.HostAndPort;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.process.AndroidProcessUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryException;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryStrategy;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryingCallable;
import com.google.devtools.mobileharness.shared.util.port.PortProber;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.FileAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupResult;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.TeardownContext;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

/**
 * Reusable MobileHarness decorator for Chrome DevTools Protocol setup on Android.
 *
 * <p>For the overall design and architecture, see <a
 * href="http://go/waddi-omnilab#heading=h.wv3r8ewb03pi">Integrating Web Testing Bridge into OmniLab
 * & FTL</a>.
 */
@DecoratorAnnotation(help = "Decorator to launch Android Chrome and configure CDP port forwarding.")
public class AndroidCdpDecorator extends LifecycleDecorator {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @ParamAnnotation(
      required = false,
      help =
          "Port parameters for adb reverse port forwarding, format: guestPort:hostPort (e.g.,"
              + " 9988:8088)")
  public static final String PARAM_LOCAL_SERVER_PORT = "local_server_port";

  @ParamAnnotation(
      required = false,
      help = "The target type of web test. Valid values: 'browser', 'webview'. Default: 'browser'.")
  public static final String PARAM_TARGET_TYPE = "target_type";

  @ParamAnnotation(
      required = false,
      help = "The package name of the app under test (specifically for WebViews).")
  public static final String PARAM_PACKAGE_NAME = "package_name";

  @ParamAnnotation(
      required = false,
      help = "The first activity name of the app under test (specifically for WebViews).")
  public static final String PARAM_ACTIVITY_NAME = "activity_name";

  @ParamAnnotation(
      required = false,
      help = "The local workstation path to the compatible chromedriver binary.")
  public static final String PARAM_CHROMEDRIVER_PATH = "chromedriver_path";

  @ParamAnnotation(
      required = false,
      help = "The host debugging port to forward the Chrome DevTools Protocol (CDP) socket to.")
  public static final String PARAM_DEBUG_PORT = "debug_port";

  @ParamAnnotation(required = false, help = "Custom Chrome command line flags.")
  public static final String PARAM_CHROME_FLAGS = "chrome_flags";

  @FileAnnotation(help = "The compatible chromedriver binary path.")
  public static final String TAG_CHROMEDRIVER = "chromedriver";

  private static final Pattern WEBVIEW_DEVTOOLS_SOCKET_PATTERN =
      Pattern.compile("webview_devtools_remote_(\\d+)");

  private static final String TARGET_TYPE_WEBVIEW = "webview";
  private static final int DEFAULT_DEBUG_PORT = 0;

  private final CommandExecutor commandExecutor;
  private final AndroidAdbUtil adbUtil;
  private final AndroidProcessUtil processUtil;
  private final Adb adb;
  private final AndroidFileUtil fileUtil;

  // Fields to share state between setUp and tearDown
  private int port;
  private final List<String> resolvedDevicePorts = new ArrayList<>();
  private CommandProcess chromedriverProcess;
  private boolean isWebViewTest;
  private String packageName;

  @Inject
  AndroidCdpDecorator(Driver decoratedDriver, TestInfo testInfo, Device device) {
    this(
        decoratedDriver,
        testInfo,
        device,
        new CommandExecutor(),
        new AndroidAdbUtil(),
        new AndroidProcessUtil(),
        new Adb(),
        new AndroidFileUtil());
  }

  @VisibleForTesting
  AndroidCdpDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      Device device,
      CommandExecutor commandExecutor,
      AndroidAdbUtil adbUtil,
      AndroidProcessUtil processUtil,
      Adb adb,
      AndroidFileUtil fileUtil) {
    super(decoratedDriver, testInfo);
    this.commandExecutor = commandExecutor;
    this.adbUtil = adbUtil;
    this.processUtil = processUtil;
    this.adb = adb;
    this.fileUtil = fileUtil;
  }

  @Override
  @SuppressWarnings("CheckReturnValue")
  protected SetupResult setUp(SetupContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    String deviceId = getDevice().getDeviceId();
    String targetTypeParam = testInfo.jobInfo().params().get(PARAM_TARGET_TYPE, "");
    String targetType =
        targetTypeParam == null ? "" : targetTypeParam.trim().toLowerCase(Locale.US);
    this.isWebViewTest = targetType.equals(TARGET_TYPE_WEBVIEW);

    this.packageName = testInfo.jobInfo().params().get(PARAM_PACKAGE_NAME, null);
    String activityName = testInfo.jobInfo().params().get(PARAM_ACTIVITY_NAME, null);

    String localServerPortParam = testInfo.jobInfo().params().get(PARAM_LOCAL_SERVER_PORT, "");
    String localServerPortText = localServerPortParam == null ? "" : localServerPortParam.trim();
    List<String> reversePortMappings = new ArrayList<>();
    if (!localServerPortText.isEmpty()) {
      for (String p : Splitter.on(',').split(localServerPortText)) {
        reversePortMappings.add(p.trim());
      }
    }

    this.port = testInfo.jobInfo().params().getInt(PARAM_DEBUG_PORT, DEFAULT_DEBUG_PORT);
    if (this.port == 0) {
      try {
        this.port = pickUnusedPort();
      } catch (IOException e) {
        throw new MobileHarnessException(
            ExtErrorId.CDP_PORT_ALLOCATION_ERROR, "Failed to pick an unused dynamic port.", e);
      }
    }

    String chromedriverPath = null;
    if (testInfo.jobInfo().files().isTagNotEmpty(TAG_CHROMEDRIVER)) {
      chromedriverPath = testInfo.jobInfo().files().getSingle(TAG_CHROMEDRIVER);
    }
    if (Strings.isNullOrEmpty(chromedriverPath)) {
      chromedriverPath = testInfo.jobInfo().params().get(PARAM_CHROMEDRIVER_PATH, null);
    }

    String chromeFlags = testInfo.jobInfo().params().get(PARAM_CHROME_FLAGS, "");
    if (!chromeFlags.isEmpty()) {
      String flagFile = this.isWebViewTest ? "webview-command-line" : "chrome-command-line";
      String pkg = this.isWebViewTest ? this.packageName : "com.android.chrome";

      if (!Strings.isNullOrEmpty(pkg)) {
        try {
          processUtil.stopApplication(UtilArgs.builder().setSerial(deviceId).build(), pkg);
        } catch (MobileHarnessException e) {
          testInfo
              .log()
              .atWarning()
              .alsoTo(logger)
              .withCause(e)
              .log("Failed to stop package %s.", pkg);
        }
      }

      try {
        Path tempFile =
            Path.of(testInfo.getTmpFileDir(), "chrome_flags_" + System.nanoTime() + ".txt");
        Files.writeString(tempFile, "_ " + chromeFlags);

        int sdkVersion =
            Integer.parseInt(
                getDevice().getDimension(Dimension.Name.SDK_VERSION).stream()
                    .findFirst()
                    .orElse("0"));

        String tmpDestPath = "/data/local/tmp/" + flagFile;
        fileUtil.push(deviceId, sdkVersion, tempFile.toString(), tmpDestPath);
        adb.runShell(deviceId, "chmod 666 " + tmpDestPath);

        if (!this.isWebViewTest) {
          String localDestPath = "/data/local/" + flagFile;
          fileUtil.push(deviceId, sdkVersion, tempFile.toString(), localDestPath);
          adb.runShell(deviceId, "chmod 666 " + localDestPath);
        }

        try {
          Files.deleteIfExists(tempFile);
        } catch (IOException e) {
          testInfo.log().atWarning().alsoTo(logger).log("Failed to delete temp file: %s", tempFile);
        }
      } catch (IOException e) {
        // Local File I/O Error on Host
        throw new MobileHarnessException(
            ExtErrorId.CDP_FLAG_SETUP_ERROR,
            "Failed to create temporary Chrome flag file on host.",
            e);
      } catch (MobileHarnessException e) {
        // Device ADB or Permission Error (e.g. running on a production user build)
        throw new MobileHarnessException(
            ExtErrorId.CDP_PORT_FORWARD_ERROR,
            String.format(
                "Failed to push or set permissions for Chrome/WebView flags (%s) on device %s. "
                    + "Ensure the device is running a userdebug build.",
                chromeFlags, deviceId),
            e);
      }
    }

    // 1. Launch Browser / App
    if (this.isWebViewTest) {
      if (Strings.isNullOrEmpty(this.packageName)) {
        throw new MobileHarnessException(
            ExtErrorId.CDP_PARAMETER_ERROR,
            "Parameter package_name is required for WebView tests.");
      }
      String targetActivity = activityName;
      if (Strings.isNullOrEmpty(targetActivity)) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("activity_name parameter not specified. Resolving default activity...");
        try {
          targetActivity = processUtil.resolveDefaultActivity(deviceId, this.packageName);
        } catch (MobileHarnessException e) {
          throw new MobileHarnessException(
              ExtErrorId.CDP_APP_LAUNCH_ERROR,
              "Failed to resolve default launch activity for package " + this.packageName,
              e);
        }
      }
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Launching WebView app package: %s, activity: %s", this.packageName, targetActivity);
      try {
        processUtil.startApplication(deviceId, this.packageName, targetActivity);
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            ExtErrorId.CDP_APP_LAUNCH_ERROR, "Failed to launch WebView app.", e);
      }
      UtilArgs appUtilArgs = UtilArgs.builder().setSerial(deviceId).build();
      try {
        String pid =
            RetryingCallable.newBuilder(
                    () -> {
                      String p = processUtil.getProcessId(appUtilArgs, this.packageName);
                      if (Strings.isNullOrEmpty(p)) {
                        throw new MobileHarnessException(
                            ExtErrorId.CDP_APP_LAUNCH_ERROR,
                            "WebView app package " + this.packageName + " PID is empty.");
                      }
                      return p;
                    },
                    RetryStrategy.uniformDelay(Duration.ofMillis(100), 30))
                .build()
                .call();
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("WebView app package %s started with PID: %s", this.packageName, pid);
      } catch (RetryException e) {
        throw new MobileHarnessException(
            ExtErrorId.CDP_APP_LAUNCH_ERROR,
            "Timed out waiting for WebView app package " + this.packageName + " to start.",
            e.getCause());
      }
    } else {
      // Standard Chrome Web Test
      testInfo.log().atInfo().alsoTo(logger).log("Launching Chrome browser cleanly");
      try {
        String chromeActivity = processUtil.resolveDefaultActivity(deviceId, "com.android.chrome");
        processUtil.startApplication(deviceId, "com.android.chrome", chromeActivity);
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            ExtErrorId.CDP_APP_LAUNCH_ERROR, "Failed to launch Chrome browser.", e);
      }
    }

    // 2. Poll for socket availability
    AtomicReference<String> detectedSocketRef = new AtomicReference<>();
    try {
      RetryingCallable.newBuilder(
              () -> {
                String checkResult =
                    adb.runShell(
                        deviceId,
                        "cat /proc/net/unix | grep -e chrome_devtools -e webview_devtools",
                        Duration.ofSeconds(10));
                if (checkResult.trim().isEmpty()) {
                  throw new MobileHarnessException(
                      ExtErrorId.CDP_APP_LAUNCH_ERROR,
                      "Chrome DevTools remote socket not detected.");
                }
                detectedSocketRef.set(checkResult);
                return true;
              },
              RetryStrategy.uniformDelay(Duration.ofMillis(500), 20))
          .build()
          .call();
      testInfo.log().atInfo().alsoTo(logger).log("Chrome DevTools remote socket is ready.");
    } catch (RetryException e) {
      if (e.getCause() instanceof InterruptedException) {
        Thread.currentThread().interrupt();
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log("Interrupted while polling for Chrome DevTools remote socket: %s", e.getMessage());
      } else {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log(
                "Warning: Chrome DevTools remote socket not detected on device before forwarding.");
      }
    }

    // 3. Port Forward DevTools
    if (this.isWebViewTest) {
      String pid = null;
      try {
        pid =
            processUtil.getProcessId(
                UtilArgs.builder().setSerial(deviceId).build(), this.packageName);
      } catch (MobileHarnessException e) {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log("Failed to resolve WebView PID: %s", e.getMessage());
      }

      String socketSuffix = pid;
      if (socketSuffix == null) {
        String checkResult = detectedSocketRef.get();
        if (checkResult != null) {
          Matcher matcher = WEBVIEW_DEVTOOLS_SOCKET_PATTERN.matcher(checkResult);
          if (matcher.find()) {
            socketSuffix = matcher.group(1);
            testInfo
                .log()
                .atInfo()
                .alsoTo(logger)
                .log("Resolved WebView PID from active socket: %s", socketSuffix);
          }
        }
      }

      if (socketSuffix == null) {
        throw new MobileHarnessException(
            ExtErrorId.CDP_PORT_FORWARD_ERROR,
            "Failed to set up adb WebView port forwarding: WebView PID is null.");
      }

      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Setting up WebView port forwarding to port %d", this.port);
      try {
        adbUtil.forwardTcpPort(
            deviceId, this.port, "localabstract:webview_devtools_remote_" + socketSuffix);
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            ExtErrorId.CDP_PORT_FORWARD_ERROR, "Failed to set up adb WebView port forwarding.", e);
      }
    } else {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Setting up Chrome port forwarding to port %d", this.port);
      try {
        adbUtil.forwardTcpPort(deviceId, this.port, "localabstract:chrome_devtools_remote");
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            ExtErrorId.CDP_PORT_FORWARD_ERROR, "Failed to set up adb Chrome port forwarding.", e);
      }
    }

    // 4. Reverse Port Forwarding
    String resolvedBaseUrl = null;
    for (String mapping : reversePortMappings) {
      String devicePort = "0";
      String hostPort = mapping;
      if (mapping.contains(":")) {
        try {
          HostAndPort hostAndPort = HostAndPort.fromString(mapping);
          devicePort = hostAndPort.getHost();
          hostPort = String.valueOf(hostAndPort.getPort());
        } catch (IllegalArgumentException e) {
          throw new MobileHarnessException(
              ExtErrorId.CDP_PARAMETER_ERROR, "Invalid reverse port mapping format: " + mapping, e);
        }
      }
      if (devicePort.equals("0")) {
        try {
          devicePort = String.valueOf(pickUnusedPort());
        } catch (IOException e) {
          throw new MobileHarnessException(
              ExtErrorId.CDP_PORT_ALLOCATION_ERROR,
              "Failed to pick an unused dynamic port for adb reverse.",
              e);
        }
      }
      this.resolvedDevicePorts.add(devicePort);
      if (resolvedBaseUrl == null) {
        resolvedBaseUrl = "http://localhost:" + devicePort;
      }
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Setting up adb reverse port forwarding for guest %s -> host %s",
              devicePort, hostPort);
      try {
        adbUtil.reverseTcpPort(deviceId, Integer.parseInt(devicePort), Integer.parseInt(hostPort));
      } catch (NumberFormatException | MobileHarnessException e) {
        throw new MobileHarnessException(
            ExtErrorId.CDP_PORT_FORWARD_ERROR,
            "Failed to set up adb reverse port forwarding for mapping: " + mapping,
            e);
      }
    }

    int chromedriverPort = 0;

    // 5. Spawn ChromeDriver in background
    if (!Strings.isNullOrEmpty(chromedriverPath)) {
      try {
        chromedriverPort = pickUnusedPort();
      } catch (IOException e) {
        throw new MobileHarnessException(
            ExtErrorId.CDP_PORT_ALLOCATION_ERROR,
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
            ExtErrorId.CDP_CHROMEDRIVER_ERROR, "Failed to start ChromeDriver.", e);
      }
      try {
        waitForPort(chromedriverPort, Duration.ofSeconds(10));
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            ExtErrorId.CDP_CHROMEDRIVER_ERROR,
            "ChromeDriver failed to bind to port " + chromedriverPort,
            e);
      }
    }

    // 6. Inject Context variables into driver's environment
    if (chromedriverPort > 0) {
      String seleniumUrl = "http://127.0.0.1:" + chromedriverPort;
      injectContextVariable(testInfo, "SELENIUM_ADDRESS", seleniumUrl);
      // Keep camelCase key for older Selenium/WebDriver drivers
      testInfo.properties().add("seleniumAddress", seleniumUrl);
    }
    if (resolvedBaseUrl != null) {
      injectContextVariable(testInfo, "BASE_URL", resolvedBaseUrl);
    }
    injectContextVariable(testInfo, "DEBUGGER_ADDRESS", "127.0.0.1:" + this.port);
    injectContextVariable(testInfo, "WEB_TEST_HTTP_SERVER", "localhost:0");

    // Dynamic browser descriptor based SUT target type and device model
    String platformSuffix = this.isWebViewTest ? "webview-android" : "chrome-android";
    String deviceModel =
        getDevice().getDimension(Dimension.Name.MODEL).stream()
            .findFirst()
            .orElse("")
            .toLowerCase(Locale.US)
            .replaceAll("[^a-z0-9_]", "_");
    String browserDescriptor =
        deviceModel.isEmpty() ? platformSuffix : platformSuffix + "-" + deviceModel;
    injectContextVariable(testInfo, "WEB_TEST_BROWSER_DESCRIPTOR", browserDescriptor);

    return SetupResult.continueDecorated();
  }

  @VisibleForTesting
  int pickUnusedPort() throws IOException, InterruptedException {
    return PortProber.pickUnusedPort();
  }

  @VisibleForTesting
  void setPort(int port) {
    this.port = port;
  }

  @VisibleForTesting
  void addResolvedDevicePort(String port) {
    this.resolvedDevicePorts.add(port);
  }

  @VisibleForTesting
  void setChromedriverProcess(CommandProcess chromedriverProcess) {
    this.chromedriverProcess = chromedriverProcess;
  }

  @VisibleForTesting
  void setIsWebViewTest(boolean isWebViewTest) {
    this.isWebViewTest = isWebViewTest;
  }

  @VisibleForTesting
  void setPackageName(String packageName) {
    this.packageName = packageName;
  }

  private void injectContextVariable(TestInfo testInfo, String name, String value) {
    // Injects into params map for XML template placeholder resolution
    testInfo.jobInfo().params().add(name, value);
    // Injects into properties for Java-to-Java metadata sharing
    testInfo.properties().add(name, value);
    // Injects with env_var_ prefix for subprocess environment export
    testInfo.properties().add("env_var_" + name, value);
  }

  @VisibleForTesting
  void waitForPort(int port, Duration timeout) throws MobileHarnessException {
    try {
      RetryingCallable.newBuilder(
              () -> {
                try {
                  InetAddress[] addresses = InetAddress.getAllByName("127.0.0.1");
                  if (addresses.length == 0) {
                    throw new MobileHarnessException(
                        ExtErrorId.CDP_CHROMEDRIVER_ERROR, "No address found for 127.0.0.1");
                  }
                  try (Socket socket = new Socket(addresses[0], port)) {
                    return true;
                  }
                } catch (IOException e) {
                  throw new MobileHarnessException(
                      ExtErrorId.CDP_CHROMEDRIVER_ERROR, "Port is not open yet.", e);
                }
              },
              RetryStrategy.uniformDelay(
                  Duration.ofMillis(100), Math.max(1, (int) (timeout.toMillis() / 100))))
          .build()
          .call();
    } catch (RetryException e) {
      throw new MobileHarnessException(
          ExtErrorId.CDP_CHROMEDRIVER_ERROR,
          String.format("Timed out waiting for port %d to open.", port),
          e.getCause());
    }
  }

  @Override
  protected void tearDown(TeardownContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    String deviceId = getDevice().getDeviceId();

    // 7. Cleanup
    try {
      if (this.chromedriverProcess != null) {
        testInfo.log().atInfo().alsoTo(logger).log("Tearing down ChromeDriver...");
        this.chromedriverProcess.kill();
      }
    } finally {
      try {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Clearing port forwarding on port %d", this.port);
        try {
          adbUtil.removeTcpPortForward(deviceId, this.port);
        } catch (MobileHarnessException e) {
          testInfo
              .log()
              .atWarning()
              .alsoTo(logger)
              .log("Failed to remove adb port forwarding: %s", e.getMessage());
        }
      } finally {
        try {
          for (String devicePort : this.resolvedDevicePorts) {
            testInfo
                .log()
                .atInfo()
                .alsoTo(logger)
                .log("Clearing adb reverse port forwarding for port %s", devicePort);
            try {
              adbUtil.removeReverseTcpPort(deviceId, Integer.parseInt(devicePort));
            } catch (NumberFormatException | MobileHarnessException e) {
              testInfo
                  .log()
                  .atWarning()
                  .alsoTo(logger)
                  .log(
                      "Failed to remove adb reverse port forwarding for port %s: %s",
                      devicePort, e.getMessage());
            }
          }
        } finally {
          UtilArgs utilArgs = UtilArgs.builder().setSerial(deviceId).build();
          if (this.isWebViewTest) {
            testInfo.log().atInfo().alsoTo(logger).log("Force stopping app %s", this.packageName);
            try {
              processUtil.stopApplication(utilArgs, this.packageName);
            } catch (MobileHarnessException e) {
              testInfo
                  .log()
                  .atWarning()
                  .alsoTo(logger)
                  .log("Failed to force-stop app: %s", e.getMessage());
            }
          } else {
            testInfo.log().atInfo().alsoTo(logger).log("Force stopping Chrome browser");
            try {
              processUtil.stopApplication(utilArgs, "com.android.chrome");
            } catch (MobileHarnessException e) {
              testInfo
                  .log()
                  .atWarning()
                  .alsoTo(logger)
                  .log("Failed to force-stop Chrome browser: %s", e.getMessage());
            }
          }
        }
      }
    }
  }
}
