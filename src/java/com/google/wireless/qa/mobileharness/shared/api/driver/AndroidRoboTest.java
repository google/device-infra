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


import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test;
import com.google.devtools.mobileharness.platform.android.appcrawler.PostProcessor;
import com.google.devtools.mobileharness.platform.android.appcrawler.PreProcessor;
import com.google.devtools.mobileharness.shared.util.port.PortProber;
import com.google.protobuf.ExtensionRegistry;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.TestAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidRoboTestSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import javax.inject.Inject;

/** Driver for running Android Robo Tests using the UTP Android Robo Driver. */
@DriverAnnotation(
    help =
        "Driver to run Android Robo tests on real devices/emulators using "
            + "the UTP AndroidRoboDriver.")
@TestAnnotation(required = false, help = "Crawls the app. No specific test to execute.")
public class AndroidRoboTest extends BaseDriver implements SpecConfigable<AndroidRoboTestSpec> {

  private static final String MH_EXCEPTION_DETAIL_PROTO_FILE_NAME = "exception-detail.pb";
  private final Aapt aapt;
  private final Adb adb;
  private final PreProcessor preProcessor;
  private final PostProcessor postProcessor;
  private final Clock clock;

  @Inject
  AndroidRoboTest(
      Device device,
      TestInfo testInfo,
      Adb adb,
      Aapt aapt,
      Clock clock,
      PreProcessor preProcessor,
      PostProcessor postProcessor) {
    super(device, testInfo);
    this.aapt = aapt;
    this.adb = adb;
    this.clock = clock;
    this.preProcessor = preProcessor;
    this.postProcessor = postProcessor;
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Running Android Robo Driver on %s.", this.getDevice().getDeviceId());
    testInfo.log().atInfo().alsoTo(logger).log("Job Info: %s", this.getTest().jobInfo());

    AndroidRoboTestSpec spec = testInfo.jobInfo().combinedSpec(this);
    testInfo.log().atInfo().alsoTo(logger).log("\n\nAndroid Robo Test Spec: \n\n%s", spec);
    preProcessor.installApks(testInfo, getDevice(), spec);

    postProcessor.uninstallApks(testInfo, getDevice(), spec);
  }

  private static int pickUnusedPort() throws InterruptedException, MobileHarnessException {
    try {
      return PortProber.pickUnusedPort();
    } catch (IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ROBO_TEST_FREE_PORT_UNAVAILABLE, "Unable to find unused port", e);
    }
  }

  private void setResult(TestInfo testInfo, TestResult result) throws MobileHarnessException {
    if (!result.equals(TestResult.ERROR)) {
      testInfo.result().set(result);
      return;
    }
    // If Error, read the exception detail proto.
    Path exceptionDetailProtoPath =
        Path.of(testInfo.getGenFileDir(), MH_EXCEPTION_DETAIL_PROTO_FILE_NAME);
    try {
      var exceptionDetail =
          ExceptionDetail.parseFrom(
              Files.readAllBytes(exceptionDetailProtoPath), ExtensionRegistry.getEmptyRegistry());
      testInfo.resultWithCause().setNonPassing(Test.TestResult.ERROR, exceptionDetail);
    } catch (IOException ex) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ROBO_TEST_MH_EXCEPTION_DETAIL_READ_ERROR,
          "Unable to exception detail proto.",
          ex);
    }
  }
}
