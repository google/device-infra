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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatLineProxy;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.time.CountDownTimer;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidLogcatMonitoringDecoratorSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidLogcatMonitoringDecorator} */
@RunWith(JUnit4.class)
public class AndroidLogcatMonitoringDecoratorTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private AndroidDevice device;
  @Mock private Driver decoratedDriver;
  @Mock Adb adb;
  @Mock LogcatLineProxy logcatLineProxy;
  @Mock CommandProcess commandProcess;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock CountDownTimer timer;

  private LocalFileUtil localFileUtil;
  private Path genFilesDir;

  @Before
  public void setUp() throws Exception {
    genFilesDir = temporaryFolder.newFolder().toPath();
    localFileUtil = new LocalFileUtil();

    when(decoratedDriver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn("deviceId");
    when(testInfo.getGenFileDir()).thenReturn(genFilesDir.toString());
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(jobInfo.timer()).thenReturn(timer);
    when(testInfo.log()).thenReturn(new Log(new Timing()));
    when(timer.remainingTimeJava()).thenReturn(Duration.ofSeconds(5));
    doNothing().when(decoratedDriver).run(testInfo);
    when(adb.runShell(any(), any())).thenReturn("01-30 10:15:20:000");
    when(adb.runShellAsync(any(), anyString(), any(), any())).thenReturn(commandProcess);
  }

  @Test
  public void decorator_run_succeeds() throws Exception {
    AndroidLogcatMonitoringDecoratorSpec spec =
        AndroidLogcatMonitoringDecoratorSpec.newBuilder()
            .addReportAsFailurePackages("com.test.me")
            .build();
    when(jobInfo.combinedSpec(any())).thenReturn(spec);
    when(logcatLineProxy.getUnparsedLines()).thenReturn(ImmutableList.of());

    AndroidLogcatMonitoringDecorator decorator =
        new AndroidLogcatMonitoringDecorator(
            decoratedDriver, testInfo, adb, logcatLineProxy, localFileUtil);

    decorator.run(testInfo);

    verify(adb).runShell("deviceId", "date +%m-%d\\ %H:%M:%S.000");
    verify(adb)
        .runShellAsync(
            "deviceId",
            "logcat -v threadtime -T \"01-30 10:15:20:000\"",
            Duration.ofSeconds(5),
            logcatLineProxy);
    verify(decoratedDriver).run(testInfo);
    verify(logcatLineProxy).getUnparsedLines();
    verify(commandProcess).killAndThenKillForcibly(Duration.ofSeconds(5));
  }

  @Test
  public void decorator_run_succeeds_and_writes_unparsedLines() throws Exception {
    AndroidLogcatMonitoringDecoratorSpec spec =
        AndroidLogcatMonitoringDecoratorSpec.newBuilder()
            .addReportAsFailurePackages("com.test.me")
            .build();
    when(jobInfo.combinedSpec(any())).thenReturn(spec);
    var line1 = "unparsed line 1";
    var line2 = "unparsed line 2";
    when(logcatLineProxy.getUnparsedLines()).thenReturn(ImmutableList.of(line1, line2));

    AndroidLogcatMonitoringDecorator decorator =
        new AndroidLogcatMonitoringDecorator(
            decoratedDriver, testInfo, adb, logcatLineProxy, localFileUtil);

    decorator.run(testInfo);

    verify(logcatLineProxy).getUnparsedLines();
    Path unparsedLogcatPath = genFilesDir.resolve("unparsed_logcat.txt");
    assertThat(Files.exists(unparsedLogcatPath)).isTrue();
    assertThat(Files.readAllLines(unparsedLogcatPath)).containsExactly(line1, line2);
  }
}
