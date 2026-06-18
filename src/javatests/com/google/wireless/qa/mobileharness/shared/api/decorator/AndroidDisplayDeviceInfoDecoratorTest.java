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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.process.AndroidProcessUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AndroidDisplayDeviceInfoDecoratorTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private Driver decorated;
  @Mock private Device device;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private ApkInstaller apkInstaller;
  @Mock private AndroidProcessUtil androidProcessUtil;
  @Mock private ResUtil resUtil;

  private AndroidDisplayDeviceInfoDecorator decorator;

  private static final String DEVICE_ID = "device_id";

  @Before
  public void setUp() throws Exception {
    when(decorated.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(jobInfo.properties()).thenReturn(new Properties(new Timing()));
    when(testInfo.log()).thenReturn(new Log(new Timing()));

    decorator =
        new AndroidDisplayDeviceInfoDecorator(
            decorated, testInfo, androidProcessUtil, apkInstaller, resUtil);
  }

  @Test
  public void run_startsBackdropActivity() throws Exception {
    when(resUtil.getExternalResourceFile(
            eq("/com/google/testing/helium/utp/android/companion/backdrop/backdrop.apk")))
        .thenReturn(Optional.of("/path/to/backdrop.apk"));

    decorator.run(testInfo);

    verify(apkInstaller)
        .installApkIfNotExist(
            eq(device),
            eq(ApkInstallArgs.builder().addApkPaths("/path/to/backdrop.apk").build()),
            any());

    verify(androidProcessUtil)
        .startApplication(
            eq(DEVICE_ID),
            eq("-n com.google.testing.helium.utp.android.companion.backdrop/.BackdropActivity"));
  }
}
