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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.runtimestats.proto.RuntimeStatsReport;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidSystemSpecUtil;
import com.google.devtools.mobileharness.shared.util.base.ProtoExtensionRegistry;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidRuntimeStatsDecoratorSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidRuntimeStatsDecorator} */
@RunWith(JUnit4.class)
public class AndroidRuntimeStatsDecoratorTest {

  private static final String DEVICE_ID = "12345";
  private static final String MACHINE_HARDWARE_NAME = "aarch64";
  private static final int NUMBER_OF_CPUS = 2;
  private static final int CPU_FREQ_IN_KHZ = 2_200_000;
  private static final int TOTAL_MEM_IN_KIB = 4096 * 1024;
  private static final int MEMORY_CLASS_IN_MB = 2048;

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private Device device;
  @Mock private Driver decoratedDriver;
  @Mock private JobInfo jobInfo;
  @Mock private TestInfo testInfo;
  @Mock private AndroidSystemSpecUtil systemSpecUtil;

  private final Log log = new Log(new Timing());
  private Path runtimeStatsFilePath;

  private AndroidRuntimeStatsDecorator decorator;

  @Before
  public void setUp() throws Exception {
    Path genFilesDir = temporaryFolder.newFolder().toPath();
    runtimeStatsFilePath = genFilesDir.resolve("runtime_stats_report.pb");
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(decoratedDriver.getDevice()).thenReturn(device);
    when(testInfo.getGenFileDir()).thenReturn(genFilesDir.toString());
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.log()).thenReturn(log);

    setUpSpec(AndroidRuntimeStatsDecoratorSpec.getDefaultInstance());

    when(systemSpecUtil.getMachineHardwareName(DEVICE_ID)).thenReturn(MACHINE_HARDWARE_NAME);
    when(systemSpecUtil.getNumberOfCpus(DEVICE_ID)).thenReturn(NUMBER_OF_CPUS);
    when(systemSpecUtil.getMaxCpuFrequency(DEVICE_ID)).thenReturn(CPU_FREQ_IN_KHZ);
    when(systemSpecUtil.getTotalMem(DEVICE_ID)).thenReturn(TOTAL_MEM_IN_KIB);
    when(systemSpecUtil.getMemoryClassInMb(DEVICE_ID)).thenReturn(MEMORY_CLASS_IN_MB);

    decorator = new AndroidRuntimeStatsDecorator(decoratedDriver, testInfo, systemSpecUtil);
  }

  @Test
  public void run_defaultSpec_callsDecoratedDriverAndCreatesEmptyReport() throws Exception {
    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    assertRuntimeStatsReport(RuntimeStatsReport.getDefaultInstance());
  }

  @Test
  public void run_collectsCpuInfo() throws Exception {
    setUpSpec(AndroidRuntimeStatsDecoratorSpec.newBuilder().setCpuInfo(true).build());

    decorator.run(testInfo);

    assertRuntimeStatsReport(
        RuntimeStatsReport.newBuilder()
            .setCpuInfo(
                RuntimeStatsReport.CpuInfo.newBuilder()
                    .setCpuArchitecture(MACHINE_HARDWARE_NAME)
                    .setCoreCount(NUMBER_OF_CPUS)
                    .setCpuSpeedKhz(CPU_FREQ_IN_KHZ))
            .build());
  }

  @Test
  public void run_collectsMemoryInfo() throws Exception {
    setUpSpec(AndroidRuntimeStatsDecoratorSpec.newBuilder().setMemoryInfo(true).build());

    decorator.run(testInfo);

    assertRuntimeStatsReport(
        RuntimeStatsReport.newBuilder()
            .setMemoryInfo(
                RuntimeStatsReport.MemoryInfo.newBuilder()
                    .setTotalMemoryKib(TOTAL_MEM_IN_KIB)
                    .setMemoryCapKib(MEMORY_CLASS_IN_MB * 1024L))
            .build());
  }

  @Test
  public void run_errorCollectingCpuInfo_logsErrorAndContinues() throws Exception {
    setUpSpec(AndroidRuntimeStatsDecoratorSpec.newBuilder().setCpuInfo(true).build());
    when(systemSpecUtil.getMachineHardwareName(DEVICE_ID))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_SYSTEM_SPEC_GET_MACHINE_HARDWARE_NAME_ERROR, "error"));

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    assertRuntimeStatsReport(
        RuntimeStatsReport.newBuilder()
            .setCpuInfo(
                RuntimeStatsReport.CpuInfo.newBuilder()
                    .setCoreCount(NUMBER_OF_CPUS)
                    .setCpuSpeedKhz(CPU_FREQ_IN_KHZ))
            .build());
  }

  private void assertRuntimeStatsReport(RuntimeStatsReport expectedReport) throws Exception {
    RuntimeStatsReport actualReport =
        RuntimeStatsReport.parseFrom(
            Files.readAllBytes(runtimeStatsFilePath),
            ProtoExtensionRegistry.getGeneratedRegistry());
    assertThat(actualReport).isEqualTo(expectedReport);
  }

  private void setUpSpec(AndroidRuntimeStatsDecoratorSpec spec) throws Exception {
    when(jobInfo.combinedSpec(any())).thenReturn(spec);
  }
}
