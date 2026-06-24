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
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.AbstractLifecycleDecorator;
import com.google.wireless.qa.mobileharness.shared.api.decorator.util.ReportLogCollectorUtil;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.ReportLogCollectorDecoratorSpec;
import java.io.File;
import java.util.logging.Level;
import javax.inject.Inject;

/** A decorator that prepares and pulls report logs. */
@DecoratorAnnotation(help = "Prepares and pulls report logs.")
public class ReportLogCollectorDecorator extends AbstractLifecycleDecorator
    implements SpecConfigable<ReportLogCollectorDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AndroidFileUtil androidFileUtil;
  private final LocalFileUtil localFileUtil;

  @Inject
  ReportLogCollectorDecorator(
      Driver decoratedDriver, TestInfo testInfo, AndroidFileUtil androidFileUtil) {
    this(decoratedDriver, testInfo, androidFileUtil, new LocalFileUtil());
  }

  @VisibleForTesting
  ReportLogCollectorDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      AndroidFileUtil androidFileUtil,
      LocalFileUtil localFileUtil) {
    super(decoratedDriver, testInfo);
    this.androidFileUtil = androidFileUtil;
    this.localFileUtil = localFileUtil;
  }

  @Override
  protected void setUp(TestInfo testInfo) throws InterruptedException {
    try {
      ReportLogCollectorDecoratorSpec spec =
          testInfo.jobInfo().combinedSpec(this, getDevice().getDeviceId());
      String destDir = spec.getDestDir();
      File resultDir = new File(testInfo.getGenFileDir());
      if (!destDir.isEmpty()) {
        resultDir = new File(resultDir, destDir);
      }
      localFileUtil.prepareDir(resultDir.getAbsolutePath());
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .at(Level.SEVERE)
          .alsoTo(logger)
          .withCause(e)
          .log("Failed to prepare result directory.");
    }
  }

  @Override
  protected void tearDown(TestInfo testInfo) throws InterruptedException {

    try {
      ReportLogCollectorDecoratorSpec spec =
          testInfo.jobInfo().combinedSpec(this, getDevice().getDeviceId());
      String srcDir = spec.getSrcDir();
      String destDir = spec.getDestDir();
      String tempDirName = spec.getTempDir();
      boolean deviceDir = spec.getDeviceDir();

      String deviceId = getDevice().getDeviceId();

      File resultDir = new File(testInfo.getGenFileDir());
      if (!destDir.isEmpty()) {
        resultDir = new File(resultDir, destDir);
      }
      localFileUtil.prepareDir(resultDir.getAbsolutePath());

      if (deviceDir) {
        tempDirName = tempDirName.replaceAll("/$", "");
        tempDirName += "-" + deviceId;
      }

      File hostReportDir = new File(testInfo.getGenFileDir(), tempDirName);
      localFileUtil.prepareDir(hostReportDir.getAbsolutePath());

      androidFileUtil.pull(deviceId, srcDir, hostReportDir.getAbsolutePath());
      ReportLogCollectorUtil.reformatRepeatedStreams(hostReportDir, localFileUtil, testInfo);
      ReportLogCollectorUtil.pullFromHost(hostReportDir, resultDir, localFileUtil, testInfo);
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .at(Level.SEVERE)
          .alsoTo(logger)
          .withCause(e)
          .log("Failed to collect report logs.");
    }
  }
}
