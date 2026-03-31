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

package com.google.wireless.qa.mobileharness.shared.api.driver;

import static com.google.protobuf.util.JavaTimeConversions.toJavaDuration;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidTestLoopTest.ANDROID_TEST_LOOP_FAILURE_MESSAGE;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidTestLoopTest.ANDROID_TEST_LOOP_TEST_END_EPOCH_MS;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidTestLoopTest.ANDROID_TEST_LOOP_TEST_START_EPOCH_MS;

import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.instrumentation.parser.AmInstrumentationParser;
import com.google.devtools.mobileharness.platform.android.instrumentation.parser.AmInstrumentationResultBuilder;
import com.google.devtools.mobileharness.platform.android.instrumentation.parser.TestTimeTrackerKt;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestSuiteResult;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.TestAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidTestLoopTestSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.inject.Inject;

/**
 * Driver for running Firebase Test Lab (FTL) Game Loop scenarios.
 *
 * <p>See https://firebase.google.com/docs/test-lab/android/game-loop for details on how apps can
 * integrate with this testing capability.
 *
 * <p>The app under test configures available scenarios in the {@code AndroidManifest.xml} via
 * {@code meta-data} entries. Scenarios are indexed starting from 1. The maximum number of allowed
 * loops is 1024.
 *
 * <p>Sample configuration with 4 loops (1 to 4):
 *
 * <pre>{@code
 * <application>
 *   <meta-data android:name="com.google.test.loops" android:value="4" />
 * </application>
 * }</pre>
 *
 * The Test Loop test uses a specific Intent to launch the app under test that needs to be
 * configured in the {@code AndroidManifest.xml}.
 *
 * <pre>{@code
 * <application>
 *   <intent-filter>
 *     <action android:name="com.google.intent.action.TEST_LOOP"/>
 *     <category android:name="android.intent.category.DEFAULT"/>
 *     <data android:mimeType="application/javascript"/>
 *   </intent-filter>
 * </application>
 * }</pre>
 */
@DriverAnnotation(
    help = "Driver to run Firebase Test Lab Game Loop (aka Test Loop) tests on Android devices.")
@TestAnnotation(required = false, help = "Runs app scenarios. No specific test to execute.")
public class AndroidTestLoopTest extends BaseDriver
    implements SpecConfigable<AndroidTestLoopTestSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String COMPANION_APK_RESOURCE_PATH =
      "/com/google/testing/platform/android/driver/testloop/companion/companion.apk";
  private static final String COMPANION_APP_PACKAGE =
      "com.google.testing.platform.android.driver.testloop.companion";
  private static final String COMPANION_RUNNER = ".TestLoopRunner";

  private final AndroidInstrumentationUtil instrumentationUtil;
  private final ApkInstaller apkInstaller;
  private final ResUtil resUtil;
  private final Ticker ticker;
  private final Clock clock;

  @Inject
  AndroidTestLoopTest(
      Device device,
      TestInfo testInfo,
      AndroidInstrumentationUtil instrumentationUtil,
      ApkInstaller apkInstaller,
      ResUtil resUtil,
      Ticker ticker,
      Clock clock) {
    super(device, testInfo);
    this.instrumentationUtil = instrumentationUtil;
    this.apkInstaller = apkInstaller;
    this.resUtil = resUtil;
    this.ticker = ticker;
    this.clock = clock;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    testInfo.log().atInfo().alsoTo(logger).log("Running AndroidTestLoopTest on %s", deviceId);

    installCompanionApp(testInfo);

    AndroidTestLoopTestSpec spec = testInfo.jobInfo().combinedSpec(this);
    String appPackage = spec.getAppPackageId();
    List<String> scenarios =
        Splitter.on(',').trimResults().omitEmptyStrings().splitToList(spec.getScenarios());
    Duration scenariosTimeout = getScenariosTimeout(testInfo, spec);
    Stopwatch stopwatch = Stopwatch.createStarted(ticker);

    testInfo
        .properties()
        .add(ANDROID_TEST_LOOP_TEST_START_EPOCH_MS, String.valueOf(clock.instant().toEpochMilli()));

    ScenarioResult scenarioResult = new ScenarioResult("0", false, Optional.empty());

    for (String scenarioId : scenarios) {
      Duration remainingTimeout = scenariosTimeout.minus(stopwatch.elapsed());
      if (!remainingTimeout.isPositive()) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Test timeout reached. Partial results will be available.");
        break;
      }
      scenarioResult = runScenario(testInfo, deviceId, appPackage, scenarioId, remainingTimeout);
      if (scenarioResult.hasAnyFailure()) {
        break;
      }
    }

    testInfo
        .properties()
        .add(ANDROID_TEST_LOOP_TEST_END_EPOCH_MS, String.valueOf(clock.instant().toEpochMilli()));

    Optional<String> failureForUser = scenarioResult.failureForUser();
    if (failureForUser.isPresent()) {
      String failureMessage = failureForUser.get();
      testInfo.properties().add(ANDROID_TEST_LOOP_FAILURE_MESSAGE, failureMessage);
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.FAIL,
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_TEST_LOOP_INSTRUMENTATION_FAILED, failureMessage));
    } else {
      testInfo.resultWithCause().setPass();
    }
  }

  private void installCompanionApp(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    String apkPath;
    try {
      apkPath = resUtil.getResourceFile(AndroidTestLoopTest.class, COMPANION_APK_RESOURCE_PATH);
    } catch (Exception e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_TEST_LOOP_COMPANION_APK_NOT_FOUND,
          "Failed to locate companion APK from resources.",
          e);
    }
    apkInstaller.installApk(
        getDevice(),
        ApkInstallArgs.builder().addApkPaths(apkPath).setGrantPermissions(true).build(),
        testInfo.log());
  }

  private ScenarioResult runScenario(
      TestInfo testInfo, String deviceId, String appPackage, String scenarioId, Duration timeout)
      throws MobileHarnessException, InterruptedException {
    testInfo.log().atInfo().alsoTo(logger).log("Running scenario #%s", scenarioId);

    InstrumentationParser lineCallbackParser = new InstrumentationParser(scenarioId);
    boolean timedOut = false;

    AndroidInstrumentationSetting setting =
        AndroidInstrumentationSetting.create(
            COMPANION_APP_PACKAGE,
            COMPANION_RUNNER,
            null,
            ImmutableMap.of(
                "packageName", appPackage,
                "scenario", scenarioId),
            /* async= */ false,
            /* showRawResults= */ true,
            /* prefixAndroidTest= */ false,
            /* noIsolatedStorage= */ false,
            /* useTestStorageService= */ false,
            /* enableCoverage= */ false);

    try {
      instrumentationUtil.instrument(deviceId, null, setting, timeout, lineCallbackParser);
    } catch (MobileHarnessException e) {
      if (e.getErrorId().equals(AndroidErrorId.ANDROID_INSTRUMENTATION_COMMAND_EXEC_TIMEOUT)) {
        timedOut = true;
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Test timeout reached. Partial results will be available.");
      } else if (e.getErrorId()
          .equals(AndroidErrorId.ANDROID_INSTRUMENTATION_COMMAND_START_ERROR)) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_TEST_LOOP_INSTRUMENTATION_ERROR,
            "Instrumentation for scenario #" + scenarioId + " failed to run.",
            e);
      } else {
        testInfo.warnings().add(e);
      }
    }

    return lineCallbackParser.done(timedOut);
  }

  private static Duration getScenariosTimeout(TestInfo testInfo, AndroidTestLoopTestSpec spec) {
    if (spec.hasScenariosTimeout()) {
      return toJavaDuration(spec.getScenariosTimeout());
    }
    Duration mhTestTimeout =
        Duration.ofMillis(testInfo.jobInfo().setting().getTimeout().getTestTimeoutMs());
    Duration defaultTimeout = mhTestTimeout.minusSeconds(30);
    return defaultTimeout.isNegative() ? Duration.ZERO : defaultTimeout;
  }

  private static record ScenarioResult(
      String scenarioId, boolean timedOut, Optional<String> failureMessage) {

    boolean hasAnyFailure() {
      return timedOut || failureMessage.isPresent();
    }

    Optional<String> failureForUser() {
      if (timedOut) {
        // That a timeout is not affecting the end result is a specific port of the FTL behavior of
        // this test type. Post-processing of test results will do further analysis if crashes
        // occurred and fail the test accordingly.
        return Optional.empty();
      }
      return failureMessage;
    }
  }

  private static class InstrumentationParser implements Supplier<LineCallback> {

    private final String scenarioId;
    private TestSuiteResult.Builder testSuiteResultBuilder;
    private AmInstrumentationParser parser;

    InstrumentationParser(String scenarioId) {
      this.scenarioId = scenarioId;
    }

    @Override
    public LineCallback get() {
      testSuiteResultBuilder = TestSuiteResult.newBuilder();
      parser =
          new AmInstrumentationParser(
              ImmutableSet.of(new AmInstrumentationResultBuilder(testSuiteResultBuilder)),
              () -> TestTimeTrackerKt.TestTimeTracker(Instant::now));

      return new LineCallback() {
        @Override
        public LineCallback.Response onLine(String line) {
          parser.parse(line);
          return LineCallback.Response.empty();
        }
      };
    }

    ScenarioResult done(boolean timedOut) {
      if (parser == null) {
        return new ScenarioResult(scenarioId, timedOut, Optional.empty());
      }
      parser.done();
      if (parser.getInstrumentationError() != null) {
        return new ScenarioResult(
            scenarioId, timedOut, Optional.of(parser.getInstrumentationError()));
      } else if (parser.getResult() == null || !parser.getResult().getSuccess()) {
        return new ScenarioResult(
            scenarioId,
            timedOut,
            Optional.of(
                "Instrumentation for scenario #"
                    + scenarioId
                    + " reported a non-successful status:\n"
                    + parser.getResult()));
      }
      return new ScenarioResult(scenarioId, timedOut, Optional.empty());
    }
  }
}
