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
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.SystemStateManager;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log.Api;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAflagsDecoratorSpec;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidAflagsDecorator}. */
@RunWith(JUnit4.class)
public final class AndroidAflagsDecoratorTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private Adb adb;
  @Mock private SystemStateManager systemStateManager;
  @Mock private Driver decoratedDriver;
  @Mock private Device device;
  @Mock private JobInfo jobInfo;
  @Mock private TestInfo testInfo;
  @Mock private Log log;
  @Mock private Api api;

  private static final String DEVICE_ID = "123456";

  private AndroidAflagsDecorator decorator;

  @Before
  public void setUp() {
    when(decoratedDriver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.log()).thenReturn(log);
    when(log.atInfo()).thenReturn(api);
    when(log.atWarning()).thenReturn(api);
    when(api.alsoTo(any(FluentLogger.class))).thenReturn(api);

    decorator = new AndroidAflagsDecorator(decoratedDriver, testInfo, adb, systemStateManager);
  }

  @Test
  public void run_emptyOverrides_skips() throws Exception {
    mockSpec(AndroidAflagsDecoratorSpec.getDefaultInstance());

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(adb, never()).runShell(anyString(), anyString());
  }

  @Test
  public void run_success_enablesAndDisablesFlags() throws Exception {
    mockSpec(
        AndroidAflagsDecoratorSpec.newBuilder()
            .addAflagsOverrides("com.android.flags.flag_a=true")
            .addAflagsOverrides("com.android.flags.flag_b=false")
            .setForceSet(false)
            .build());

    String aflagsListOutput =
        """
        com.android.flags.flag_a    disabled  -  default   read-write system
        com.android.flags.flag_b    enabled   -  default   read-write system
        """;
    when(adb.runShell(DEVICE_ID, "aflags list")).thenReturn(aflagsListOutput);
    when(adb.runShell(DEVICE_ID, "aflags enable 'com.android.flags.flag_a'")).thenReturn("");
    when(adb.runShell(DEVICE_ID, "aflags disable 'com.android.flags.flag_b'")).thenReturn("");
    when(adb.runShell(DEVICE_ID, "aflags unset 'com.android.flags.flag_a'")).thenReturn("");
    when(adb.runShell(DEVICE_ID, "aflags unset 'com.android.flags.flag_b'")).thenReturn("");

    decorator.run(testInfo);

    // Verify pre-test updates
    verify(adb).runShell(DEVICE_ID, "aflags enable 'com.android.flags.flag_a'");
    verify(adb).runShell(DEVICE_ID, "aflags disable 'com.android.flags.flag_b'");

    verify(decoratedDriver).run(testInfo);

    // Verify post-test restores
    verify(adb).runShell(DEVICE_ID, "aflags unset 'com.android.flags.flag_a'");
    verify(adb).runShell(DEVICE_ID, "aflags unset 'com.android.flags.flag_b'");
    verify(systemStateManager, times(2)).reboot(eq(device), any(), any());
  }

  @Test
  public void run_success_forceSet() throws Exception {
    mockSpec(
        AndroidAflagsDecoratorSpec.newBuilder()
            .addAflagsOverrides("com.android.flags.flag_a=true")
            .setForceSet(true)
            .build());

    // Flag is already enabled, but forceSet=true should set it anyway
    String aflagsListOutput =
        "com.android.flags.flag_a    enabled  -  default   read-write system\n";
    when(adb.runShell(DEVICE_ID, "aflags list")).thenReturn(aflagsListOutput);
    when(adb.runShell(DEVICE_ID, "aflags enable 'com.android.flags.flag_a'")).thenReturn("");
    when(adb.runShell(DEVICE_ID, "aflags unset 'com.android.flags.flag_a'")).thenReturn("");

    decorator.run(testInfo);

    verify(adb).runShell(DEVICE_ID, "aflags enable 'com.android.flags.flag_a'");
    verify(decoratedDriver).run(testInfo);
    verify(adb).runShell(DEVICE_ID, "aflags unset 'com.android.flags.flag_a'");
  }

  @Test
  public void run_invalidFlagFormat_skips() throws Exception {
    mockSpec(
        AndroidAflagsDecoratorSpec.newBuilder()
            .addAflagsOverrides("com.android.flags.flag_a=invalid_bool")
            .setForceSet(false)
            .build());

    String aflagsListOutput =
        "com.android.flags.flag_a    disabled  -  default   read-write system\n";
    when(adb.runShell(DEVICE_ID, "aflags list")).thenReturn(aflagsListOutput);

    decorator.run(testInfo);

    verify(adb, never()).runShell(eq(DEVICE_ID), startsWith("aflags enable"));
    verify(decoratedDriver).run(testInfo);
  }

  @Test
  public void run_readOnlyFlag_skips() throws Exception {
    mockSpec(
        AndroidAflagsDecoratorSpec.newBuilder()
            .addAflagsOverrides("com.android.flags.flag_a=true")
            .setForceSet(false)
            .build());

    String aflagsListOutput =
        "com.android.flags.flag_a    disabled  -  default   read-only system\n";
    when(adb.runShell(DEVICE_ID, "aflags list")).thenReturn(aflagsListOutput);

    decorator.run(testInfo);

    verify(adb, never()).runShell(eq(DEVICE_ID), startsWith("aflags enable"));
    verify(decoratedDriver).run(testInfo);
  }

  @Test
  public void run_flagNotFound_skips() throws Exception {
    mockSpec(
        AndroidAflagsDecoratorSpec.newBuilder()
            .addAflagsOverrides("com.android.flags.non_existent_flag=true")
            .setForceSet(false)
            .build());

    String aflagsListOutput =
        "com.android.flags.flag_a    disabled  -  default   read-write system\n";
    when(adb.runShell(DEVICE_ID, "aflags list")).thenReturn(aflagsListOutput);

    decorator.run(testInfo);

    verify(adb, never()).runShell(eq(DEVICE_ID), startsWith("aflags enable"));
    verify(decoratedDriver).run(testInfo);
  }

  @Test
  public void run_invalidAflagsListLine_skips() throws Exception {
    mockSpec(
        AndroidAflagsDecoratorSpec.newBuilder()
            .addAflagsOverrides("com.android.flags.flag_a=true")
            .setForceSet(false)
            .build());

    String aflagsListOutput =
        """
        invalid_line_without_enough_parts
        com.android.flags.flag_a    disabled  -  default   read-write system
        """;
    when(adb.runShell(DEVICE_ID, "aflags list")).thenReturn(aflagsListOutput);

    decorator.run(testInfo);

    verify(adb).runShell(DEVICE_ID, "aflags enable 'com.android.flags.flag_a'");
    verify(decoratedDriver).run(testInfo);
  }

  @Test
  public void run_aflagsListEmpty_throwsException() throws Exception {
    mockSpec(
        AndroidAflagsDecoratorSpec.newBuilder()
            .addAflagsOverrides("com.android.flags.flag_a=true")
            .setForceSet(false)
            .build());
    when(adb.runShell(DEVICE_ID, "aflags list")).thenReturn("");

    assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));
  }

  @Test
  public void run_adbError_throwsException() throws Exception {
    mockSpec(
        AndroidAflagsDecoratorSpec.newBuilder()
            .addAflagsOverrides("com.android.flags.flag_a=true")
            .setForceSet(false)
            .build());

    String aflagsListOutput =
        "com.android.flags.flag_a    disabled  -  default   read-write system\n";
    when(adb.runShell(DEVICE_ID, "aflags list")).thenReturn(aflagsListOutput);
    when(adb.runShell(DEVICE_ID, "aflags enable 'com.android.flags.flag_a'"))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_AFLAGS_DECORATOR_UPDATE_FLAG_ERROR, "adb error"));

    assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));
  }

  @Test
  public void run_rebootError_throwsException() throws Exception {
    mockSpec(
        AndroidAflagsDecoratorSpec.newBuilder()
            .addAflagsOverrides("com.android.flags.flag_a=true")
            .setForceSet(false)
            .build());

    String aflagsListOutput =
        "com.android.flags.flag_a    disabled  -  default   read-write system\n";
    when(adb.runShell(DEVICE_ID, "aflags list")).thenReturn(aflagsListOutput);
    when(adb.runShell(DEVICE_ID, "aflags enable 'com.android.flags.flag_a'")).thenReturn("");

    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_AFLAGS_DECORATOR_REBOOT_ERROR, "reboot error"))
        .when(systemStateManager)
        .reboot(eq(device), any(), any());

    assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));
  }

  @Test
  public void deviceFeatureFlag_invalidFormat_throwsException() {
    MobileHarnessException e =
        assertThrows(
            MobileHarnessException.class,
            () -> new AndroidAflagsDecorator.DeviceFeatureFlag("invalid_flag_without_equals"));
    assertThat(e.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_AFLAGS_DECORATOR_FLAG_OVERRIDE_FORMAT_INVALID);
  }

  @Test
  public void aFlagsFeatureFlag_invalidFormat_throwsException() {
    MobileHarnessException e =
        assertThrows(
            MobileHarnessException.class,
            () -> new AndroidAflagsDecorator.AFlagsFeatureFlag("not_enough_parts"));
    assertThat(e.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_AFLAGS_DECORATOR_FLAG_LIST_FORMAT_INVALID);
  }

  private void mockSpec(AndroidAflagsDecoratorSpec spec) throws Exception {
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);
  }
}
