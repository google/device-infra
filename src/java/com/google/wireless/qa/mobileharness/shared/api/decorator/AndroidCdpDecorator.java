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
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.net.HostAndPort;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
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
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
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
import java.util.Optional;
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

  private static final String PROPERTY_REVERSE_DEVICE_PORTS = "MH_REVERSE_DEVICE_PORTS";
  private static final String PROPERTY_DEBUGGER_ADDRESS = "DEBUGGER_ADDRESS";
  private static final String PROPERTY_SELENIUM_ADDRESS = "SELENIUM_ADDRESS";
  private static final String PROPERTY_SELENIUM_ADDRESS_LEGACY = "seleniumAddress";
  private static final String PROPERTY_BASE_URL = "BASE_URL";
  private static final String PROPERTY_WEB_TEST_HTTP_SERVER = "WEB_TEST_HTTP_SERVER";
  private static final String PROPERTY_WEB_TEST_BROWSER_DESCRIPTOR = "WEB_TEST_BROWSER_DESCRIPTOR";
  private static final String ENV_VAR_PREFIX = "env_var_";

  private static final String DEFAULT_CHROME_PACKAGE = "com.android.chrome";
  private static final String CHROME_COMMAND_LINE_FILE = "chrome-command-line";
  private static final String WEBVIEW_COMMAND_LINE_FILE = "webview-command-line";

  // Dummy argv[0] token required because Chromium on Android discards the first argument.
  private static final String CHROME_COMMAND_LINE_ARGV0 = "_";

  private static final String TEMP_CHROME_FLAGS_FILE_PREFIX = "chrome_flags_";
  private static final String DATA_LOCAL_TMP_DIR = "/data/local/tmp/";
  private static final String DATA_LOCAL_DIR = "/data/local/";
  private static final String CHROME_DEVTOOLS_SOCKET = "localabstract:chrome_devtools_remote";
  private static final String WEBVIEW_DEVTOOLS_SOCKET_PREFIX =
      "localabstract:webview_devtools_remote_";
  private static final String LOCALHOST_IP = "127.0.0.1";

  private final CommandExecutor commandExecutor;
  private final AndroidAdbUtil adbUtil;
  private final AndroidProcessUtil processUtil;
  private final Adb adb;
  private final AndroidFileUtil fileUtil;

  // Sole instance variable retained across lifecycle (live OS process cannot be serialized)
  private CommandProcess chromedriverProcess;

  @Inject
  AndroidCdpDecorator(Driver decoratedDriver, TestInfo testInfo) {
    this(
        decoratedDriver,
        testInfo,
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
  protected SetupResult setUp(SetupContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    ImmutableList<String> reversePortMappings = parseReversePortMappings(testInfo);
    String deviceId = getDevice().getDeviceId();
    int port = allocateDebugPort(testInfo);
    configureChromeFlags(testInfo, deviceId);
    Optional<String> appPid = launchTargetApplication(testInfo, deviceId);
    Optional<String> detectedSocket = waitForDevToolsSocket(testInfo, deviceId, appPid);
    String resolvedBaseUrl =
        setupPortForwarding(testInfo, deviceId, detectedSocket, reversePortMappings, appPid, port);
    int chromedriverPort = startChromeDriverIfConfigured(testInfo);

    injectContextVariables(testInfo, resolvedBaseUrl, chromedriverPort, port);
    return SetupResult.continueDecorated();
  }

  private static boolean isWebViewTest(TestInfo testInfo) {
    String targetTypeParam = testInfo.jobInfo().params().get(PARAM_TARGET_TYPE, "");
    String targetType =
        targetTypeParam == null ? "" : targetTypeParam.trim().toLowerCase(Locale.US);
    return targetType.equals(TARGET_TYPE_WEBVIEW);
  }

  private static String getPackageName(TestInfo testInfo, boolean isWebView) {
    return testInfo
        .jobInfo()
        .params()
        .get(PARAM_PACKAGE_NAME, isWebView ? null : DEFAULT_CHROME_PACKAGE);
  }

  private static ImmutableList<String> parseReversePortMappings(TestInfo testInfo)
      throws MobileHarnessException {
    String localServerPortParam = testInfo.jobInfo().params().get(PARAM_LOCAL_SERVER_PORT, "");
    String localServerPortText = localServerPortParam == null ? "" : localServerPortParam.trim();
    if (localServerPortText.isEmpty()) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<String> reversePortMappings = ImmutableList.builder();
    for (String p : Splitter.on(',').split(localServerPortText)) {
      String mapping = p.trim();
      if (mapping.isEmpty()) {
        continue;
      }
      validateReversePortMapping(mapping);
      reversePortMappings.add(mapping);
    }
    return reversePortMappings.build();
  }

  private static void validateReversePortMapping(String mapping) throws MobileHarnessException {
    if (mapping.contains(":")) {
      try {
        HostAndPort hostAndPort = HostAndPort.fromString(mapping);
        int guestPort = Integer.parseInt(hostAndPort.getHost());
        int hostPort = hostAndPort.getPort();
        if (guestPort < 0 || hostPort < 0 || guestPort > 65535 || hostPort > 65535) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_CDP_PARAMETER_ERROR,
              "Invalid reverse port mapping format: " + mapping);
        }
      } catch (IllegalArgumentException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_CDP_PARAMETER_ERROR,
            "Invalid reverse port mapping format: " + mapping,
            e);
      }
    } else {
      try {
        int hostPort = Integer.parseInt(mapping);
        if (hostPort < 0 || hostPort > 65535) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_CDP_PARAMETER_ERROR,
              "Invalid reverse port mapping format: " + mapping);
        }
      } catch (NumberFormatException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_CDP_PARAMETER_ERROR,
            "Invalid reverse port mapping format: " + mapping,
            e);
      }
    }
  }

  private int allocateDebugPort(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    int port = testInfo.jobInfo().params().getInt(PARAM_DEBUG_PORT, DEFAULT_DEBUG_PORT);
    if (port == 0) {
      try {
        port = PortProber.pickUnusedPort();
      } catch (IOException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_CDP_PORT_ALLOCATION_ERROR,
            "Failed to pick an unused dynamic port.",
            e);
      }
    }
    return port;
  }

  private void configureChromeFlags(TestInfo testInfo, String deviceId)
      throws MobileHarnessException, InterruptedException {
    String chromeFlags = testInfo.jobInfo().params().get(PARAM_CHROME_FLAGS, "");
    if (chromeFlags.isEmpty()) {
      return;
    }
    boolean isWebView = isWebViewTest(testInfo);
    String flagFile = isWebView ? WEBVIEW_COMMAND_LINE_FILE : CHROME_COMMAND_LINE_FILE;
    String pkg = getPackageName(testInfo, isWebView);

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

    Path tempFile =
        Path.of(
            testInfo.getTmpFileDir(), TEMP_CHROME_FLAGS_FILE_PREFIX + System.nanoTime() + ".txt");
    try {
      Files.writeString(tempFile, CHROME_COMMAND_LINE_ARGV0 + " " + chromeFlags);
    } catch (IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_CDP_FLAG_SETUP_ERROR,
          "Failed to create temporary Chrome flag file on host.",
          e);
    }

    try {
      Integer sdkVersion = ((AndroidDevice) getDevice()).getSdkVersion();
      int sdkVersionInt = sdkVersion == null ? 0 : sdkVersion;

      String tmpDestPath = DATA_LOCAL_TMP_DIR + flagFile;
      fileUtil.push(deviceId, sdkVersionInt, tempFile.toString(), tmpDestPath);
      var unused1 = adb.runShell(deviceId, "chmod 666 " + tmpDestPath);

      if (!isWebView) {
        String localDestPath = DATA_LOCAL_DIR + flagFile;
        fileUtil.push(deviceId, sdkVersionInt, tempFile.toString(), localDestPath);
        var unused2 = adb.runShell(deviceId, "chmod 666 " + localDestPath);
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_CDP_PORT_FORWARD_ERROR,
          String.format(
              "Failed to push or set permissions for Chrome/WebView flags (%s) on device %s. "
                  + "Ensure the device is running a userdebug build.",
              chromeFlags, deviceId),
          e);
    } finally {
      try {
        Files.deleteIfExists(tempFile);
      } catch (IOException e) {
        testInfo.log().atWarning().alsoTo(logger).log("Failed to delete temp file: %s", tempFile);
      }
    }
  }

  private Optional<String> launchTargetApplication(TestInfo testInfo, String deviceId)
      throws MobileHarnessException, InterruptedException {
    boolean isWebView = isWebViewTest(testInfo);
    String pkg = getPackageName(testInfo, isWebView);

    if (isWebView) {
      String pid = launchWebViewApplication(testInfo, deviceId, pkg);
      return Optional.of(pid);
    }

    launchChromeBrowser(testInfo, deviceId, pkg);
    return Optional.empty();
  }

  private String launchWebViewApplication(TestInfo testInfo, String deviceId, String pkg)
      throws MobileHarnessException, InterruptedException {
    String targetActivity = testInfo.jobInfo().params().get(PARAM_ACTIVITY_NAME, null);
    if (Strings.isNullOrEmpty(targetActivity)) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("activity_name parameter not specified. Resolving default activity...");
      try {
        targetActivity = processUtil.resolveDefaultActivity(deviceId, pkg);
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_CDP_APP_LAUNCH_ERROR,
            "Failed to resolve default launch activity for package " + pkg,
            e);
      }
    }
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Launching WebView app package: %s, activity: %s", pkg, targetActivity);
    try {
      processUtil.startApplication(deviceId, pkg, targetActivity);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_CDP_APP_LAUNCH_ERROR, "Failed to launch WebView app.", e);
    }
    UtilArgs appUtilArgs = UtilArgs.builder().setSerial(deviceId).build();
    try {
      String pid =
          RetryingCallable.newBuilder(
                  () -> {
                    String p = processUtil.getProcessId(appUtilArgs, pkg);
                    if (Strings.isNullOrEmpty(p)) {
                      throw new MobileHarnessException(
                          AndroidErrorId.ANDROID_CDP_APP_LAUNCH_ERROR,
                          "WebView app package " + pkg + " PID is empty.");
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
          .log("WebView app package %s started with PID: %s", pkg, pid);
      return pid;
    } catch (RetryException e) {
      if (e.getCause() instanceof InterruptedException interruptedException) {
        throw interruptedException;
      }
      if (Thread.interrupted()) {
        throw new InterruptedException(
            "Interrupted waiting for WebView app package " + pkg + " to start.");
      }
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_CDP_APP_LAUNCH_ERROR,
          "Timed out waiting for WebView app package " + pkg + " to start.",
          e.getCause());
    }
  }

  private void launchChromeBrowser(TestInfo testInfo, String deviceId, String pkg)
      throws MobileHarnessException, InterruptedException {
    String browserPkg = Strings.isNullOrEmpty(pkg) ? DEFAULT_CHROME_PACKAGE : pkg;
    testInfo.log().atInfo().alsoTo(logger).log("Launching Chrome browser cleanly (%s)", browserPkg);
    try {
      String chromeActivity = processUtil.resolveDefaultActivity(deviceId, browserPkg);
      processUtil.startApplication(deviceId, browserPkg, chromeActivity);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_CDP_APP_LAUNCH_ERROR, "Failed to launch Chrome browser.", e);
    }
  }

  private Optional<String> waitForDevToolsSocket(
      TestInfo testInfo, String deviceId, Optional<String> appPid) throws InterruptedException {
    AtomicReference<String> detectedSocketRef = new AtomicReference<>();
    boolean isWebView = isWebViewTest(testInfo);
    String grepPattern =
        isWebView && appPid.isPresent() && !appPid.get().isEmpty()
            ? "webview_devtools_remote_" + appPid.get()
            : "-e chrome_devtools -e webview_devtools";
    try {
      RetryingCallable.newBuilder(
              () -> {
                String checkResult =
                    adb.runShell(
                        deviceId,
                        "cat /proc/net/unix | grep " + grepPattern,
                        Duration.ofSeconds(10));
                if (checkResult.trim().isEmpty()) {
                  throw new MobileHarnessException(
                      AndroidErrorId.ANDROID_CDP_APP_LAUNCH_ERROR,
                      "Chrome DevTools remote socket not detected.");
                }
                detectedSocketRef.set(checkResult);
                return true;
              },
              RetryStrategy.uniformDelay(Duration.ofMillis(500), 20))
          .build()
          .call();
      testInfo.log().atInfo().alsoTo(logger).log("Chrome DevTools remote socket is ready.");
      return Optional.ofNullable(detectedSocketRef.get());
    } catch (RetryException e) {
      if (e.getCause() instanceof InterruptedException interruptedException) {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log("Interrupted while polling for Chrome DevTools remote socket: %s", e.getMessage());
        throw interruptedException;
      }
      if (Thread.interrupted()) {
        throw new InterruptedException(
            "Interrupted while polling for Chrome DevTools remote socket.");
      }
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log("Warning: Chrome DevTools remote socket not detected on device before forwarding.");
      return Optional.empty();
    }
  }

  @VisibleForTesting
  String setupPortForwarding(
      TestInfo testInfo,
      String deviceId,
      Optional<String> detectedSocket,
      ImmutableList<String> reversePortMappings,
      Optional<String> appPid,
      int port)
      throws MobileHarnessException, InterruptedException {
    boolean isWebView = isWebViewTest(testInfo);
    String pkg = getPackageName(testInfo, isWebView);
    if (isWebView) {
      String socketSuffix = appPid.filter(pid -> !pid.isEmpty()).orElse(null);
      if (Strings.isNullOrEmpty(socketSuffix)) {
        try {
          socketSuffix =
              processUtil.getProcessId(UtilArgs.builder().setSerial(deviceId).build(), pkg);
        } catch (MobileHarnessException e) {
          testInfo
              .log()
              .atWarning()
              .alsoTo(logger)
              .log("Failed to resolve WebView PID: %s", e.getMessage());
        }
      }

      if (socketSuffix == null && detectedSocket.isPresent()) {
        Pattern targetPattern =
            appPid.isEmpty() || appPid.get().isEmpty()
                ? WEBVIEW_DEVTOOLS_SOCKET_PATTERN
                : Pattern.compile("webview_devtools_remote_(" + Pattern.quote(appPid.get()) + ")");
        Matcher matcher = targetPattern.matcher(detectedSocket.get());
        if (matcher.find()) {
          socketSuffix = matcher.group(1);
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log("Resolved WebView PID from active socket: %s", socketSuffix);
        }
      }

      if (socketSuffix == null) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_CDP_PORT_FORWARD_ERROR,
            "Failed to set up adb WebView port forwarding: WebView PID is null.");
      }

      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Setting up WebView port forwarding to port %d", port);
      try {
        adbUtil.forwardTcpPort(deviceId, port, WEBVIEW_DEVTOOLS_SOCKET_PREFIX + socketSuffix);
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_CDP_PORT_FORWARD_ERROR,
            "Failed to set up adb WebView port forwarding.",
            e);
      }
    } else {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Setting up Chrome port forwarding to port %d", port);
      try {
        adbUtil.forwardTcpPort(deviceId, port, CHROME_DEVTOOLS_SOCKET);
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_CDP_PORT_FORWARD_ERROR,
            "Failed to set up adb Chrome port forwarding.",
            e);
      }
    }

    String resolvedBaseUrl = null;
    List<String> resolvedDevicePorts = new ArrayList<>();
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
              AndroidErrorId.ANDROID_CDP_PARAMETER_ERROR,
              "Invalid reverse port mapping format: " + mapping,
              e);
        }
      }
      if (devicePort.equals("0")) {
        try {
          devicePort = String.valueOf(PortProber.pickUnusedPort());
        } catch (IOException e) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_CDP_PORT_ALLOCATION_ERROR,
              "Failed to pick an unused dynamic port for adb reverse.",
              e);
        }
      }
      resolvedDevicePorts.add(devicePort);
      testInfo
          .properties()
          .add(PROPERTY_REVERSE_DEVICE_PORTS, String.join(",", resolvedDevicePorts));
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
            AndroidErrorId.ANDROID_CDP_PORT_FORWARD_ERROR,
            "Failed to set up adb reverse port forwarding for mapping: " + mapping,
            e);
      }
    }
    return resolvedBaseUrl;
  }

  private int startChromeDriverIfConfigured(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    String chromedriverPath = null;
    if (testInfo.jobInfo().files().isTagNotEmpty(TAG_CHROMEDRIVER)) {
      chromedriverPath = testInfo.jobInfo().files().getSingle(TAG_CHROMEDRIVER);
    }
    if (Strings.isNullOrEmpty(chromedriverPath)) {
      chromedriverPath = testInfo.jobInfo().params().get(PARAM_CHROMEDRIVER_PATH, null);
    }
    if (Strings.isNullOrEmpty(chromedriverPath)) {
      return 0;
    }

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
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_CDP_CHROMEDRIVER_ERROR,
          "ChromeDriver failed to bind to port " + chromedriverPort,
          e);
    }
    return chromedriverPort;
  }

  private void injectContextVariables(
      TestInfo testInfo, String resolvedBaseUrl, int chromedriverPort, int port) {
    if (chromedriverPort > 0) {
      String seleniumUrl = "http://" + LOCALHOST_IP + ":" + chromedriverPort;
      injectContextVariable(testInfo, PROPERTY_SELENIUM_ADDRESS, seleniumUrl);
      testInfo.properties().add(PROPERTY_SELENIUM_ADDRESS_LEGACY, seleniumUrl);
    }
    if (resolvedBaseUrl != null) {
      injectContextVariable(testInfo, PROPERTY_BASE_URL, resolvedBaseUrl);
    }
    injectContextVariable(testInfo, PROPERTY_DEBUGGER_ADDRESS, LOCALHOST_IP + ":" + port);
    injectContextVariable(testInfo, PROPERTY_WEB_TEST_HTTP_SERVER, "localhost:0");

    boolean isWebView = isWebViewTest(testInfo);
    String platformSuffix = isWebView ? "webview-android" : "chrome-android";
    String deviceModel =
        getDevice().getDimension("model").stream()
            .findFirst()
            .orElse("")
            .toLowerCase(Locale.US)
            .replaceAll("[^a-z0-9_]", "_");
    String browserDescriptor =
        deviceModel.isEmpty() ? platformSuffix : platformSuffix + "-" + deviceModel;
    injectContextVariable(testInfo, PROPERTY_WEB_TEST_BROWSER_DESCRIPTOR, browserDescriptor);
  }

  private void injectContextVariable(TestInfo testInfo, String name, String value) {
    // Injects into properties for Java-to-Java metadata sharing
    testInfo.properties().add(name, value);
    // Injects with env_var_ prefix for subprocess environment export
    testInfo.properties().add(ENV_VAR_PREFIX + name, value);
  }

  @VisibleForTesting
  void waitForPort(int port, Duration timeout) throws MobileHarnessException, InterruptedException {
    try {
      RetryingCallable.newBuilder(
              () -> {
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
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_CDP_CHROMEDRIVER_ERROR,
          String.format("Timed out waiting for port %d to open.", port),
          e.getCause());
    }
  }

  @Override
  protected void tearDown(TeardownContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    String deviceId = getDevice().getDeviceId();

    try {
      cleanupChromeDriver(testInfo);
    } finally {
      try {
        cleanupPortForwarding(testInfo, deviceId);
      } finally {
        try {
          cleanupReversePortForwarding(testInfo, deviceId);
        } finally {
          try {
            cleanupTargetApplication(testInfo, deviceId);
          } finally {
            cleanupCommandFlagFiles(testInfo, deviceId);
          }
        }
      }
    }
  }

  private void cleanupChromeDriver(TestInfo testInfo) {
    if (this.chromedriverProcess != null) {
      testInfo.log().atInfo().alsoTo(logger).log("Tearing down ChromeDriver...");
      this.chromedriverProcess.kill();
    }
  }

  private void cleanupPortForwarding(TestInfo testInfo, String deviceId)
      throws InterruptedException {
    String debuggerAddress = testInfo.properties().get(PROPERTY_DEBUGGER_ADDRESS);
    int port = 0;
    if (!Strings.isNullOrEmpty(debuggerAddress)) {
      try {
        HostAndPort hostAndPort = HostAndPort.fromString(debuggerAddress);
        if (hostAndPort.hasPort()) {
          port = hostAndPort.getPort();
        }
      } catch (IllegalArgumentException e) {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log("Failed to parse DEBUGGER_ADDRESS: %s", debuggerAddress);
      }
    }
    if (port > 0) {
      testInfo.log().atInfo().alsoTo(logger).log("Clearing port forwarding on port %d", port);
      try {
        adbUtil.removeTcpPortForward(deviceId, port);
      } catch (MobileHarnessException e) {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log("Failed to remove adb port forwarding: %s", e.getMessage());
      }
    }
  }

  private void cleanupReversePortForwarding(TestInfo testInfo, String deviceId)
      throws InterruptedException {
    String reversePortsStr = testInfo.properties().get(PROPERTY_REVERSE_DEVICE_PORTS);
    if (!Strings.isNullOrEmpty(reversePortsStr)) {
      for (String devicePort :
          Splitter.on(',').omitEmptyStrings().trimResults().split(reversePortsStr)) {
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
    }
  }

  private void cleanupTargetApplication(TestInfo testInfo, String deviceId)
      throws InterruptedException {
    UtilArgs utilArgs = UtilArgs.builder().setSerial(deviceId).build();
    boolean isWebView = isWebViewTest(testInfo);
    String pkg = getPackageName(testInfo, isWebView);
    if (isWebView) {
      if (!Strings.isNullOrEmpty(pkg)) {
        testInfo.log().atInfo().alsoTo(logger).log("Force stopping app %s", pkg);
        try {
          processUtil.stopApplication(utilArgs, pkg);
        } catch (MobileHarnessException e) {
          testInfo
              .log()
              .atWarning()
              .alsoTo(logger)
              .log("Failed to force-stop app: %s", e.getMessage());
        }
      }
    } else {
      String browserPkg = Strings.isNullOrEmpty(pkg) ? DEFAULT_CHROME_PACKAGE : pkg;
      if (!Strings.isNullOrEmpty(browserPkg)) {
        testInfo.log().atInfo().alsoTo(logger).log("Force stopping Chrome browser");
        try {
          processUtil.stopApplication(utilArgs, browserPkg);
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

  private void cleanupCommandFlagFiles(TestInfo testInfo, String deviceId)
      throws InterruptedException {
    boolean isWebView = isWebViewTest(testInfo);
    String flagFile = isWebView ? WEBVIEW_COMMAND_LINE_FILE : CHROME_COMMAND_LINE_FILE;
    try {
      var unused1 = adb.runShell(deviceId, "rm -f " + DATA_LOCAL_TMP_DIR + flagFile);
      if (!isWebView) {
        var unused2 = adb.runShell(deviceId, "rm -f " + DATA_LOCAL_DIR + flagFile);
      }
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log("Failed to remove flag files: %s", e.getMessage());
    }
  }
}
