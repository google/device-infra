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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.runtimestats.proto.RuntimeStatsReport;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidSystemSpecUtil;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidRuntimeStatsDecoratorSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;

/**
 * Decorator for collecting device runtime stats.
 *
 * <p>Writes the {@link RuntimeStatsReport} to the file "runtime_stats_report.pb" in the gen file
 * directory. Collection of each stat is best effort. See {@link AndroidRuntimeStatsDecoratorSpec}
 * for supported stats.
 */
public class AndroidRuntimeStatsDecorator extends BaseDecorator
    implements SpecConfigable<AndroidRuntimeStatsDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AndroidSystemSpecUtil systemSpecUtil;

  @Inject
  AndroidRuntimeStatsDecorator(
      Driver decorated, TestInfo testInfo, AndroidSystemSpecUtil androidSystemSpecUtil) {
    super(decorated, testInfo);
    this.systemSpecUtil = androidSystemSpecUtil;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    collectAndWriteReport(testInfo);
    getDecorated().run(testInfo);
  }

  private void collectAndWriteReport(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    AndroidRuntimeStatsDecoratorSpec spec = testInfo.jobInfo().combinedSpec(this);
    RuntimeStatsReport.Builder reportBuilder = RuntimeStatsReport.newBuilder();
    if (spec.getCpuInfo()) {
      collectCpuInfo(testInfo, reportBuilder);
    }
    if (spec.getMemoryInfo()) {
      collectMemoryInfo(testInfo, reportBuilder);
    }
    writeRuntimeStatsReport(testInfo, reportBuilder.build());
  }

  private void collectCpuInfo(TestInfo testInfo, RuntimeStatsReport.Builder reportBuilder)
      throws InterruptedException {
    String deviceId = getDevice().getDeviceId();
    try {
      reportBuilder
          .getCpuInfoBuilder()
          .setCpuArchitecture(systemSpecUtil.getMachineHardwareName(deviceId));
    } catch (MobileHarnessException e) {
      logCollectError(testInfo, e);
    }
    try {
      reportBuilder.getCpuInfoBuilder().setCoreCount(systemSpecUtil.getNumberOfCpus(deviceId));
    } catch (MobileHarnessException e) {
      logCollectError(testInfo, e);
    }
    int frequency = systemSpecUtil.getMaxCpuFrequency(deviceId);
    if (frequency != 0) {
      reportBuilder.getCpuInfoBuilder().setCpuSpeedKhz(frequency);
    }
  }

  private void collectMemoryInfo(TestInfo testInfo, RuntimeStatsReport.Builder reportBuilder)
      throws InterruptedException {
    String deviceId = getDevice().getDeviceId();
    try {
      reportBuilder.getMemoryInfoBuilder().setTotalMemoryKib(systemSpecUtil.getTotalMem(deviceId));
    } catch (MobileHarnessException e) {
      logCollectError(testInfo, e);
    }
    try {
      int memoryClassInMb = systemSpecUtil.getMemoryClassInMb(deviceId);
      if (memoryClassInMb != 0) {
        reportBuilder.getMemoryInfoBuilder().setMemoryCapKib(memoryClassInMb * 1024L);
      }
    } catch (MobileHarnessException e) {
      logCollectError(testInfo, e);
    }
  }

  private void writeRuntimeStatsReport(TestInfo testInfo, RuntimeStatsReport report)
      throws MobileHarnessException {
    try {
      Files.write(
          Path.of(testInfo.getGenFileDir()).resolve("runtime_stats_report.pb"),
          report.toByteArray());
    } catch (IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_RUNTIME_STATS_DECORATOR_WRITE_REPORT_ERROR,
          "Failed to write runtime stats report",
          e);
    }
  }

  private void logCollectError(TestInfo testInfo, MobileHarnessException e) {
    testInfo
        .log()
        .atWarning()
        .withCause(e)
        .alsoTo(logger)
        .log("Error collecting runtime stats (ignoring).");
  }
}
