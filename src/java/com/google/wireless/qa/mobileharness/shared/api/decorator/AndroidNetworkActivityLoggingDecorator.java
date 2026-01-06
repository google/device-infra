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

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.networkactivitylogging.proto.NetworkActivityLoggingReport;
import com.google.devtools.mobileharness.platform.android.networkactivitylogging.proto.TcpConnectEvent;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidSettings;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryException;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryStrategy;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryingCallable;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.devtools.omnilab.deviceadmin.proto.NetworkEvent;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.ExtensionRegistry;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidNetworkActivityLoggingDecoratorSpec;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.inject.Inject;

/**
 * Records network events of applications on the device.
 *
 * <p>Recording network events is best-effort only.
 *
 * <p>The decorator uses the MTaaS DeviceAdmin app to:
 *
 * <ol>
 *   <li>Enable network logging at the beginning of the test
 *   <li>Force a log dump at the end of the test
 *   <li>Writes a {@link NetworkActivityLoggingReport} proto to a file named {@code
 *       network_activity_logging_report.pb}
 * </ol>
 *
 * <p>The MTaaS DeviceAdmin app needs to be pre-installed on the device and be the device owner.
 * This is the case for Core Lab devices, or certain OmniLab-managed labs.
 *
 * <p>See https://developer.android.com/work/dpc/logging for more details about the network activity
 * logging feature.
 */
public class AndroidNetworkActivityLoggingDecorator extends BaseDecorator
    implements SpecConfigable<AndroidNetworkActivityLoggingDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String DEVICE_ADMIN_PACKAGE_ID = "com.google.android.apps.mtaas.deviceadmin";
  private static final String ENABLE_NETWORK_LOGGING_INSTRUMENTATION = ".EnableNetworkLogging";

  /** Path the MTaaS DeviceAdmin app writes the network logs to. */
  private static final String DEVICE_LOG_PATH = "/sdcard/deviceadmin/network_events.dpb";

  /** Output file name for the network activity logging report. */
  private static final String REPORT_FILE_NAME = "network_activity_logging_report.pb";

  private static final Duration SHORT_COMMAND_TIMEOUT = Duration.ofSeconds(20);

  private final Adb adb;
  private final AndroidAdbUtil adbUtil;
  private final AndroidFileUtil fileUtil;
  private final AndroidPackageManagerUtil packageManagerUtil;
  private final AndroidInstrumentationUtil instrumentationUtil;
  private final AndroidSystemSettingUtil systemSettingUtil;
  private final Sleeper sleeper;

  @Inject
  AndroidNetworkActivityLoggingDecorator(
      Driver decorated,
      TestInfo testInfo,
      Adb adb,
      AndroidAdbUtil adbUtil,
      AndroidFileUtil fileUtil,
      AndroidPackageManagerUtil packageManagerUtil,
      AndroidInstrumentationUtil instrumentationUtil,
      AndroidSystemSettingUtil systemSettingUtil,
      Sleeper sleeper) {
    super(decorated, testInfo);
    this.adb = adb;
    this.adbUtil = adbUtil;
    this.fileUtil = fileUtil;
    this.packageManagerUtil = packageManagerUtil;
    this.instrumentationUtil = instrumentationUtil;
    this.systemSettingUtil = systemSettingUtil;
    this.sleeper = sleeper;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    AndroidNetworkActivityLoggingDecoratorSpec spec =
        testInfo.jobInfo().combinedSpec(this, getDevice().getDeviceId());

    boolean deviceAdminAvailable = checkDeviceAdminAvailability();

    if (deviceAdminAvailable) {
      enableLogging(testInfo);
      tryDismissNotification();
    }

    try {
      getDecorated().run(testInfo);
    } finally {
      if (deviceAdminAvailable) {
        triggerLogDump(testInfo);
        Path hostDumpFile = Path.of(testInfo.getTmpFileDir(), "network_events.dpb");
        try {
          pullDumpFile(testInfo, hostDumpFile);
          writeReportFile(testInfo, hostDumpFile, spec);
        } catch (MobileHarnessException e) {
          logger.atWarning().withCause(e).log("Failed to process network logs.");
        }
      }
    }
  }

  private boolean checkDeviceAdminAvailability()
      throws MobileHarnessException, InterruptedException {
    try {
      return packageManagerUtil.isPackageEnabled(
          getDevice().getDeviceId(), DEVICE_ADMIN_PACKAGE_ID);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_NETWORK_ACTIVITY_LOGGING_DECORATOR_CHECK_DEVICE_ADMIN_ERROR,
          "Failed to check if device admin is available",
          e);
    }
  }

  private void enableLogging(TestInfo testInfo) throws InterruptedException {
    try {
      int deviceSdkVersion = systemSettingUtil.getDeviceSdkVersion(getDevice().getDeviceId());
      String result =
          instrumentationUtil.instrument(
              getDevice().getDeviceId(),
              deviceSdkVersion,
              AndroidInstrumentationSetting.create(
                  DEVICE_ADMIN_PACKAGE_ID,
                  ENABLE_NETWORK_LOGGING_INSTRUMENTATION,
                  /* className= */ null,
                  /* otherOptions= */ null,
                  /* async= */ false,
                  /* showRawResults= */ true,
                  /* prefixAndroidTest= */ false,
                  /* noIsolatedStorage= */ false,
                  /* useTestStorageService= */ false,
                  /* enableCoverage= */ false),
              /* timeout= */ SHORT_COMMAND_TIMEOUT);
      if (!result.contains("INSTRUMENTATION_CODE: -1")) {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log("Enabling network logging failed. Result: %s", result);
      }

    } catch (MobileHarnessException e) {
      testInfo.log().atWarning().withCause(e).alsoTo(logger).log("Enabling network logging failed");
    }
  }

  private void tryDismissNotification() throws MobileHarnessException, InterruptedException {
    String result =
        runSettingsCommand(
            AndroidSettings.Spec.create(
                AndroidSettings.Command.GET,
                AndroidSettings.NameSpace.GLOBAL,
                "heads_up_notifications_enabled"));

    if (!result.trim().equals("1")) {
      // If the heads up notifications are already disabled, we don't need to do anything.
      return;
    }

    // Enabling network logging triggers a heads-up notification which can get in the way of test.
    // As a best effort to mitigate this, we wait for a bit and then:
    //  1. Disable heads up notifications that dismisses all notifications.
    //  2. Enable heads up notifications back for tests.
    sleeper.sleep(Duration.ofSeconds(5));
    runSettingsCommand(
        AndroidSettings.Spec.create(
            AndroidSettings.Command.PUT,
            AndroidSettings.NameSpace.GLOBAL,
            "heads_up_notifications_enabled 0"));
    runSettingsCommand(
        AndroidSettings.Spec.create(
            AndroidSettings.Command.PUT,
            AndroidSettings.NameSpace.GLOBAL,
            "heads_up_notifications_enabled 1"));
  }

  @CanIgnoreReturnValue
  private String runSettingsCommand(AndroidSettings.Spec spec)
      throws MobileHarnessException, InterruptedException {
    try {
      UtilArgs utilArgs = UtilArgs.builder().setSerial(getDevice().getDeviceId()).build();
      return adbUtil.settings(utilArgs, spec, SHORT_COMMAND_TIMEOUT);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_NETWORK_ACTIVITY_LOGGING_DECORATOR_SETTINGS_COMMAND_ERROR,
          "Failed to run settings command: " + spec,
          e);
    }
  }

  private void triggerLogDump(TestInfo testInfo) throws InterruptedException {
    logger.atInfo().log("Triggering network log dump");
    try {
      String unused =
          adb.runShellWithRetry(
              getDevice().getDeviceId(), "dpm force-network-logs", SHORT_COMMAND_TIMEOUT);
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .withCause(e)
          .alsoTo(logger)
          .log("Failed to force dump of network logs");
    }
  }

  private void pullDumpFile(TestInfo testInfo, Path destPath) throws InterruptedException {
    // In tests, it took up to 15 seconds from triggering network log dump to file being persisted
    // on the sdcard, thus such long backoff.
    RetryStrategy strategy =
        RetryStrategy.exponentialBackoff(
            /* firstDelay= */ Duration.ofSeconds(2), /* multiplier= */ 2.0, /* numAttempts= */ 4);

    try {
      RetryingCallable.newBuilder(
              (Callable<Void>)
                  () -> {
                    fileUtil.pull(
                        getDevice().getDeviceId(),
                        DEVICE_LOG_PATH,
                        destPath.toAbsolutePath().toString());
                    return null;
                  },
              strategy)
          .setThrowStrategy(RetryingCallable.ThrowStrategy.THROW_LAST)
          .build()
          .call();
    } catch (RetryException e) {
      Throwable cause = e.getCause();
      if (cause instanceof InterruptedException) {
        Thread.currentThread().interrupt();
        throw new InterruptedException(Throwables.getStackTraceAsString(cause));
      }
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Pulling network dump file failed. This can be expected if no network requests were"
                  + " made during the test.");
    }
  }

  private void writeReportFile(
      TestInfo testInfo, Path dumpFile, AndroidNetworkActivityLoggingDecoratorSpec spec)
      throws MobileHarnessException {
    if (!Files.exists(dumpFile)) {
      return;
    }

    Path reportFile = Path.of(testInfo.getGenFileDir(), REPORT_FILE_NAME);
    NetworkActivityLoggingReport.Builder reportBuilder = NetworkActivityLoggingReport.newBuilder();

    try (InputStream in = new BufferedInputStream(Files.newInputStream(dumpFile))) {
      dumpFileToReport(in, reportBuilder, spec);
    } catch (IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_NETWORK_ACTIVITY_LOGGING_DECORATOR_TRANSFORM_LOGS_ERROR,
          "Failed to read network events from file.",
          e);
    }

    try (BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(reportFile))) {
      reportBuilder.build().writeTo(out);
    } catch (IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_NETWORK_ACTIVITY_LOGGING_DECORATOR_WRITE_REPORT_ERROR,
          "Failed to write network activity logging report to file.",
          e);
    }
  }

  private static void dumpFileToReport(
      InputStream in,
      NetworkActivityLoggingReport.Builder reportBuilder,
      AndroidNetworkActivityLoggingDecoratorSpec spec)
      throws IOException {
    ImmutableSet<String> packagesToFilter = ImmutableSet.copyOf(spec.getPackageIdsList());
    Map<String, String> addressToHostMapping = new HashMap<>();
    List<NetworkEvent> connectEvents = new ArrayList<>();
    NetworkEvent event;

    while ((event = NetworkEvent.parseDelimitedFrom(in, ExtensionRegistry.getEmptyRegistry()))
        != null) {
      if (!packagesToFilter.isEmpty() && !packagesToFilter.contains(event.getPackageId())) {
        continue;
      }
      switch (event.getDataCase()) {
        case CONNECT -> connectEvents.add(event);
        case DNS -> {
          for (String address : event.getDns().getHostAddressesList()) {
            addressToHostMapping.put(address, event.getDns().getHostname());
          }
        }
        default -> {}
      }
    }

    for (NetworkEvent connectEvent : connectEvents) {
      String hostAddress = connectEvent.getConnect().getHostAddress();
      String hostname = addressToHostMapping.getOrDefault(hostAddress, hostAddress);
      TcpConnectEvent tcpEvent =
          TcpConnectEvent.newBuilder()
              .setTimestamp(connectEvent.getTimestamp())
              .setPackageId(connectEvent.getPackageId())
              .setHostname(hostname)
              .setHostAddress(hostAddress)
              .setPort(connectEvent.getConnect().getPort())
              .build();
      reportBuilder.addTcpConnectEvents(tcpEvent);
    }
  }
}
