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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.TeardownContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.util.StepSkippableLifecycleDecoratorUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAtsDynamicConfigPusherDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAtsDynamicConfigPusherDecoratorSpec.TestTarget;
import java.io.File;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AndroidAtsDynamicConfigPusherDecoratorTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private Driver decorated;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Device device;
  @Mock private LocalFileUtil localFileUtil;
  @Mock private AndroidFileUtil androidFileUtil;
  @Mock private AndroidSystemSettingUtil androidSystemSettingUtil;
  @Mock private ApkInstaller apkInstaller;
  @Mock private ResUtil resUtil;

  private Properties properties;
  private AndroidAtsDynamicConfigPusherDecorator decorator;
  private AndroidAtsDynamicConfigPusherDecoratorSpec spec;

  @Before
  public void setUp() throws Exception {
    properties = new Properties(new Timing());
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.log()).thenReturn(new Log(new Timing()));
    when(jobInfo.properties()).thenReturn(properties);
    when(decorated.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn("device_id");

    TestLocator mockLocator = mock(TestLocator.class);
    when(mockLocator.getId()).thenReturn("test_id");
    when(testInfo.locator()).thenReturn(mockLocator);

    spec =
        AndroidAtsDynamicConfigPusherDecoratorSpec.newBuilder()
            .setXtsSuiteInfo("suite_name=cts")
            .setTarget(TestTarget.DEVICE)
            .setCleanup(true)
            .setHasServerSideConfig(false)
            .build();
    when(jobInfo.combinedSpec(
            ArgumentMatchers.<SpecConfigable<AndroidAtsDynamicConfigPusherDecoratorSpec>>any(),
            eq("device_id")))
        .thenReturn(spec);
    decorator =
        new AndroidAtsDynamicConfigPusherDecorator(
            decorated,
            testInfo,
            localFileUtil,
            androidFileUtil,
            androidSystemSettingUtil,
            apkInstaller,
            resUtil);
  }

  @Test
  public void skippableSetUp_success() throws Exception {
    when(resUtil.getResourceFile(any(), any())).thenReturn("apk_path");
    when(apkInstaller.installApkIfNotExist(any(), any(), any())).thenReturn("content_provider_pkg");

    File tempDir = Files.createTempDirectory("test").toFile();
    File configFile = new File(tempDir, "cts.dynamic");
    configFile.createNewFile();
    try (Writer writer = Files.newBufferedWriter(configFile.toPath(), UTF_8)) {
      writer.write("<dynamicConfig></dynamicConfig>");
    }
    configFile.deleteOnExit();
    tempDir.deleteOnExit();
    List<File> files = new ArrayList<>();
    files.add(configFile);
    when(localFileUtil.listFiles(any(), eq(true))).thenReturn(files);

    decorator.skippableSetUp(SetupContext.create(testInfo));

    verify(androidFileUtil).push(eq("device_id"), anyInt(), any(), any());
    verify(apkInstaller).installApkIfNotExist(eq(device), any(), any());

    assertThat(
            StepSkippableLifecycleDecoratorUtil.getState(
                jobInfo,
                "device_id",
                "com.google.wireless.qa.mobileharness.shared.api.decorator.AndroidAtsDynamicConfigPusherDecorator",
                "device_file_pushed_path"))
        .isPresent();
    assertThat(
            StepSkippableLifecycleDecoratorUtil.getState(
                jobInfo,
                "device_id",
                "com.google.wireless.qa.mobileharness.shared.api.decorator.AndroidAtsDynamicConfigPusherDecorator",
                "content_provider"))
        .hasValue("content_provider_pkg");
  }

  @Test
  public void skippableTearDown_success() throws Exception {
    StepSkippableLifecycleDecoratorUtil.setState(
        jobInfo,
        "device_id",
        "com.google.wireless.qa.mobileharness.shared.api.decorator.AndroidAtsDynamicConfigPusherDecorator",
        "device_file_pushed_path",
        "device_path");
    StepSkippableLifecycleDecoratorUtil.setState(
        jobInfo,
        "device_id",
        "com.google.wireless.qa.mobileharness.shared.api.decorator.AndroidAtsDynamicConfigPusherDecorator",
        "content_provider",
        "content_provider_pkg");

    decorator.skippableTearDown(TeardownContext.create(testInfo, null, null));

    verify(androidFileUtil).removeFiles("device_id", "device_path");
    verify(apkInstaller).uninstallApk(eq(device), eq("content_provider_pkg"), eq(true), any());
  }

  @Test
  public void skippableSetUp_extractFromResource_resourceNotFound_throwsException()
      throws Exception {
    spec =
        AndroidAtsDynamicConfigPusherDecoratorSpec.newBuilder()
            .setExtractFromResource(true)
            .setConfigFilename("cts")
            .setTarget(TestTarget.DEVICE)
            .setCleanup(true)
            .build();
    when(jobInfo.combinedSpec(
            ArgumentMatchers.<SpecConfigable<AndroidAtsDynamicConfigPusherDecoratorSpec>>any(),
            eq("device_id")))
        .thenReturn(spec);

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class,
            () -> decorator.skippableSetUp(SetupContext.create(testInfo)));
    assertThat(exception).hasMessageThat().contains("Fail to find 'cts.dynamic' in tradefed jar");
  }

  @Test
  public void skippableSetUp_emptyXtsSuiteInfo_success() throws Exception {
    spec =
        AndroidAtsDynamicConfigPusherDecoratorSpec.newBuilder()
            .setTarget(TestTarget.DEVICE)
            .setCleanup(true)
            .setHasServerSideConfig(false)
            .setConfigFilename("cts")
            .build();
    when(jobInfo.combinedSpec(
            ArgumentMatchers.<SpecConfigable<AndroidAtsDynamicConfigPusherDecoratorSpec>>any(),
            eq("device_id")))
        .thenReturn(spec);

    when(resUtil.getResourceFile(any(), any())).thenReturn("apk_path");
    when(apkInstaller.installApkIfNotExist(any(), any(), any())).thenReturn("content_provider_pkg");

    File tempDir = Files.createTempDirectory("test").toFile();
    File configFile = new File(tempDir, "cts.dynamic");
    configFile.createNewFile();
    try (Writer writer = Files.newBufferedWriter(configFile.toPath(), UTF_8)) {
      writer.write("<dynamicConfig></dynamicConfig>");
    }
    configFile.deleteOnExit();
    tempDir.deleteOnExit();
    List<File> files = new ArrayList<>();
    files.add(configFile);
    when(localFileUtil.listFiles(any(), eq(true))).thenReturn(files);

    decorator.skippableSetUp(SetupContext.create(testInfo));

    verify(androidFileUtil).push(eq("device_id"), anyInt(), any(), any());
  }
}
