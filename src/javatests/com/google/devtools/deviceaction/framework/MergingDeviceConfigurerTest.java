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

package com.google.devtools.deviceaction.framework;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.ActionConfig;
import com.google.devtools.deviceaction.common.schemas.ActionOptions;
import com.google.devtools.deviceaction.common.schemas.ActionOptions.Options;
import com.google.devtools.deviceaction.common.schemas.Command;
import com.google.devtools.deviceaction.framework.deviceconfigs.DeviceConfigDao;
import com.google.devtools.deviceaction.framework.devices.Devices;
import com.google.devtools.deviceaction.framework.proto.ActionSpec;
import com.google.devtools.deviceaction.framework.proto.AndroidPhoneSpec;
import com.google.devtools.deviceaction.framework.proto.DeviceConfig;
import com.google.devtools.deviceaction.framework.proto.DeviceSpec;
import com.google.devtools.deviceaction.framework.proto.DeviceType;
import com.google.devtools.deviceaction.framework.proto.FileSpec;
import com.google.devtools.deviceaction.framework.proto.GCSFile;
import com.google.devtools.deviceaction.framework.proto.Operand;
import com.google.devtools.deviceaction.framework.proto.Unary;
import com.google.devtools.deviceaction.framework.proto.action.InstallMainlineSpec;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class MergingDeviceConfigurerTest {

  private static final String TEST_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/deviceaction/testdata/google_pixel-7_s_userdebug.textproto");
  private static final String ID_1 = "id1";
  private static final FileSpec GCS_FILE =
      FileSpec.newBuilder()
          .setTag("train_folder")
          .setGcsFile(
              GCSFile.newBuilder().setProject("fake project").setGsUri("gs://bucket/d1/o1").build())
          .build();
  private static final FileSpec LOCAL_FILE_1 =
      FileSpec.newBuilder().setTag("mainline_modules").setLocalPath("/local/path/f1").build();
  private static final FileSpec LOCAL_FILE_2 =
      FileSpec.newBuilder().setTag("mainline_modules").setLocalPath("/local/path/f2").build();
  private static final InstallMainlineSpec INSTALL_MAINLINE_SPEC =
      InstallMainlineSpec.newBuilder()
          .setEnableRollback(true)
          .setCleanUpSessions(false)
          .addFiles(LOCAL_FILE_1)
          .addFiles(LOCAL_FILE_2)
          .addFiles(GCS_FILE)
          .build();
  private static final ActionSpec ACTION_SPEC =
      ActionSpec.newBuilder()
          .setUnary(
              Unary.newBuilder()
                  .setFirst(
                      Operand.newBuilder()
                          .setDeviceType(DeviceType.ANDROID_PHONE)
                          .setUuid(ID_1)
                          .build())
                  .setExtension(InstallMainlineSpec.ext, INSTALL_MAINLINE_SPEC)
                  .build())
          .build();
  private static final DeviceSpec DEVICE_SPEC =
      DeviceSpec.newBuilder()
          .setAndroidPhoneSpec(
              AndroidPhoneSpec.newBuilder()
                  .setBrand("Google")
                  .setNeedDisablePackageCache(false)
                  .build())
          .build();
  private static final DeviceConfig DEVICE_CONFIG =
      DeviceConfig.newBuilder()
          .setDeviceSpec(DEVICE_SPEC)
          .setExtension(
              InstallMainlineSpec.installMainlineSpec,
              InstallMainlineSpec.newBuilder().setCleanUpSessions(true).build())
          .build();
  private static final ActionConfig ACTION_CONFIG =
      ActionConfig.builder()
          .setCmd(Command.INSTALL_MAINLINE)
          .setActionSpec(ACTION_SPEC)
          .setFirstSpec(DEVICE_SPEC)
          .build();
  private static final Options OPTIONS = buildOptions();

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private Devices mockDevices;

  @Mock private DeviceConfigDao mockDao;

  private MergingDeviceConfigurer configurer;

  @Before
  public void setUp() throws Exception {
    when(mockDao.getDeviceConfig(anyString(), eq(Command.INSTALL_MAINLINE)))
        .thenReturn(DEVICE_CONFIG);
    when(mockDevices.getDeviceKey(
            Operand.newBuilder().setDeviceType(DeviceType.ANDROID_PHONE).setUuid(ID_1).build()))
        .thenReturn("google_pixel-7_s_userdebug");
    configurer = new MergingDeviceConfigurer(mockDao, mockDevices);
  }

  @Test
  public void getConfigure_useActionSpec() throws Exception {
    ActionConfig actionConfig =
        configurer.createActionConfigure(Command.INSTALL_MAINLINE, ACTION_SPEC);

    assertThat(actionConfig).isEqualTo(ACTION_CONFIG);
    verify(mockDao).getDeviceConfig(anyString(), eq(Command.INSTALL_MAINLINE));
  }

  @Test
  public void getConfigure_getDeviceConfigFromDao() throws Exception {
    ActionOptions actionOptions = buildActionOptions(/* useDao= */ true);

    ActionConfig actionConfig = configurer.createActionConfigure(actionOptions);

    assertThat(actionConfig).isEqualTo(ACTION_CONFIG);
    verify(mockDao).getDeviceConfig(anyString(), eq(Command.INSTALL_MAINLINE));
  }

  @Test
  public void getConfigure_getDeviceConfigFromDao_throwException() throws Exception {
    ActionOptions actionOptions = buildActionOptions(/* useDao= */ true);
    when(mockDao.getDeviceConfig(anyString(), eq(Command.INSTALL_MAINLINE)))
        .thenThrow(DeviceActionException.class);

    assertThrows(
        DeviceActionException.class, () -> configurer.createActionConfigure(actionOptions));
    verify(mockDao).getDeviceConfig(anyString(), eq(Command.INSTALL_MAINLINE));
  }

  @Test
  public void getConfigure_getDeviceConfigFromUser() throws Exception {
    ActionOptions actionOptions = buildActionOptions(/* useDao= */ false);

    ActionConfig actionConfig = configurer.createActionConfigure(actionOptions);

    assertThat(actionConfig).isEqualTo(ACTION_CONFIG);
    verify(mockDao, never()).getDeviceConfig(anyString(), eq(Command.INSTALL_MAINLINE));
  }

  private static Options buildOptions() {
    try {
      return Options.builder()
          .addTrueBoolOptions("enable_rollback")
          .addFalseBoolOptions("clean_up_sessions")
          .addKeyValues("not exist", "not used")
          .addFileOptions("mainline_modules", "/local/path/f1", "/local/path/f2")
          .addFileOptions("train_folder", "gcs:fake project&gs://bucket/d1/o1")
          .build();
    } catch (Exception e) {
      throw new RuntimeException("This never happens!", e);
    }
  }

  private static ActionOptions buildActionOptions(boolean useDao) throws Exception {
    Options firstDevice =
        useDao
            ? Options.builder().addKeyValues("serial", ID_1).build()
            : Options.builder()
                .addKeyValues("serial", ID_1)
                .addKeyValues("device_config", TEST_FILE_PATH)
                .build();
    return ActionOptions.builder()
        .setCommand(Command.INSTALL_MAINLINE)
        .setAction(OPTIONS)
        .setFirstDevice(firstDevice)
        .build();
  }
}
