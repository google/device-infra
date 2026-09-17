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

package com.google.devtools.mobileharness.platform.android.chromedriver.plugin;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidChromeDriverProviderDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AndroidChromeDriverFetcherPluginTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Mock private SystemUtil systemUtil;
  @Mock private TestStartingEvent event;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Allocation allocation;
  @Mock private Log testLog;
  @Mock private Log.Api loggingApi;
  @Mock private Files testFiles;
  @Mock private DeviceLocator deviceLocator;

  private Params jobParams;
  private Path chromedriverBaseDir;
  private AndroidChromeDriverFetcherPlugin plugin;

  @Before
  public void setUp() throws Exception {
    chromedriverBaseDir = tempFolder.newFolder("chromedriver").toPath();
    jobParams = new Params(null);

    when(event.getTest()).thenReturn(testInfo);
    when(event.getAllocation()).thenReturn(allocation);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.files()).thenReturn(testFiles);
    when(testInfo.log()).thenReturn(testLog);
    when(testLog.atInfo()).thenReturn(loggingApi);
    when(loggingApi.alsoTo(any(FluentLogger.class))).thenReturn(loggingApi);
    when(jobInfo.params()).thenReturn(jobParams);
    when(systemUtil.isOnMac()).thenReturn(false);

    plugin = new AndroidChromeDriverFetcherPlugin(systemUtil, chromedriverBaseDir);
  }

  @Test
  public void onTestStarting_resolvesDriverFromDeviceDimension() throws Exception {
    when(jobInfo.type())
        .thenReturn(
            JobType.newBuilder().addDecorator("AndroidChromeDriverProviderDecorator").build());

    Path versionDir = chromedriverBaseDir.resolve("linux/v136_0_7103_60");
    java.nio.file.Files.createDirectories(versionDir);
    Path binaryPath = versionDir.resolve("chromedriver");
    java.nio.file.Files.createFile(binaryPath);

    ImmutableListMultimap<String, String> dimensions =
        ImmutableListMultimap.of(Dimension.Name.CHROME_VERSION.lowerCaseName(), "136.0.7103.60");
    when(allocation.getAllDevices()).thenReturn(ImmutableMap.of(deviceLocator, dimensions));

    plugin.onTestStarting(event);

    ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
    verify(testFiles)
        .add(
            eq(AndroidChromeDriverProviderDecoratorSpec.FILE_CHROMEDRIVER_BINARIES),
            pathCaptor.capture());
    assertThat(pathCaptor.getValue()).isEqualTo(binaryPath.toAbsolutePath().toString());
  }

  @Test
  public void onTestStarting_resolvesDriverFromJobParam() throws Exception {
    when(jobInfo.type())
        .thenReturn(
            JobType.newBuilder().addDecorator("AndroidChromeDriverProviderDecorator").build());

    Path versionDir = chromedriverBaseDir.resolve("linux/v136_0_7103_60");
    java.nio.file.Files.createDirectories(versionDir);
    Path binaryPath = versionDir.resolve("chromedriver");
    java.nio.file.Files.createFile(binaryPath);

    jobParams.add(AndroidChromeDriverFetcherPlugin.PARAM_CHROME_VERSION, "136.0.7103.60");
    when(allocation.getAllDevices()).thenReturn(ImmutableMap.of());

    plugin.onTestStarting(event);

    ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
    verify(testFiles)
        .add(
            eq(AndroidChromeDriverProviderDecoratorSpec.FILE_CHROMEDRIVER_BINARIES),
            pathCaptor.capture());
    assertThat(pathCaptor.getValue()).isEqualTo(binaryPath.toAbsolutePath().toString());
  }
}
