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

package com.google.devtools.deviceaction.common.utils;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.devtools.deviceaction.common.utils.Constants.DEVICE_CONFIG_KEY;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toProtoDuration;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.ActionOptions;
import com.google.devtools.deviceaction.common.schemas.ActionOptions.Options;
import com.google.devtools.deviceaction.common.schemas.Command;
import com.google.devtools.deviceaction.common.schemas.DevicePosition;
import com.google.devtools.deviceaction.common.schemas.DeviceWrapper;
import com.google.devtools.deviceaction.framework.proto.ActionSpec;
import com.google.devtools.deviceaction.framework.proto.AndroidPhoneSpec;
import com.google.devtools.deviceaction.framework.proto.Binary;
import com.google.devtools.deviceaction.framework.proto.DeviceConfig;
import com.google.devtools.deviceaction.framework.proto.DeviceSpec;
import com.google.devtools.deviceaction.framework.proto.DeviceType;
import com.google.devtools.deviceaction.framework.proto.FileSpec;
import com.google.devtools.deviceaction.framework.proto.GCSFile;
import com.google.devtools.deviceaction.framework.proto.Nullary;
import com.google.devtools.deviceaction.framework.proto.Operand;
import com.google.devtools.deviceaction.framework.proto.Unary;
import com.google.devtools.deviceaction.framework.proto.action.InstallMainlineSpec;
import com.google.devtools.deviceaction.framework.proto.action.ResetOption;
import com.google.devtools.deviceaction.framework.proto.action.ResetSpec;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ProtoHelperTest {

  private static final String TEST_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/deviceaction/testdata/google_pixel-7_s_userdebug.textproto");

  private static final String ID_1 = "id1";
  private static final String ID_2 = "id2";
  private static final Operand DEVICE_1 =
      Operand.newBuilder().setDeviceType(DeviceType.ANDROID_PHONE).setUuid(ID_1).build();
  private static final Operand DEVICE_2 =
      Operand.newBuilder().setDeviceType(DeviceType.ANDROID_PHONE).setUuid(ID_2).build();
  private static final Nullary NULLARY = Nullary.getDefaultInstance();
  private static final Unary UNARY = Unary.newBuilder().setFirst(DEVICE_1).build();
  private static final Binary BINARY =
      Binary.newBuilder().setFirst(DEVICE_1).setSecond(DEVICE_2).build();
  public static final AndroidPhoneSpec GOOGLE_SPEC =
      AndroidPhoneSpec.newBuilder().setBrand("Google").build();
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
  private static final FileSpec LOCAL_FILE_3 =
      FileSpec.newBuilder().setTag("recovery_modules").setLocalPath("/local/path/f1").build();
  private static final FileSpec LOCAL_FILE_4 =
      FileSpec.newBuilder().setTag("recovery_modules").setLocalPath("/local/path/f2").build();
  private static final InstallMainlineSpec FROM_DEVICE_CONFIG =
      InstallMainlineSpec.newBuilder()
          .setCleanUpSessions(false)
          .addFiles(GCS_FILE)
          .setEnableRollback(true)
          .build();
  private static final InstallMainlineSpec FROM_CMD =
      InstallMainlineSpec.newBuilder()
          .setCleanUpSessions(true)
          .addFiles(LOCAL_FILE_1)
          .setDevKeySigned(true)
          .build();
  private static final InstallMainlineSpec COMPLETE_INSTALL_MAINLINE =
      InstallMainlineSpec.newBuilder()
          .setEnableRollback(true)
          .setCleanUpSessions(false)
          .addFiles(LOCAL_FILE_1)
          .addFiles(LOCAL_FILE_2)
          .addFiles(GCS_FILE)
          .build();

  private static final ResetSpec COMPLETE_RESET =
      ResetSpec.newBuilder()
          .setNeedPreloadModulesRecovery(true)
          .setResetOption(ResetOption.TEST_HARNESS)
          .addFiles(LOCAL_FILE_3)
          .addFiles(LOCAL_FILE_4)
          .build();
  private static final DeviceConfig DEVICE_CONFIG =
      DeviceConfig.newBuilder()
          .setDeviceSpec(DeviceSpec.newBuilder().setAndroidPhoneSpec(GOOGLE_SPEC))
          .setExtension(InstallMainlineSpec.installMainlineSpec, FROM_DEVICE_CONFIG)
          .build();

  private static final DeviceSpec DEVICE_SPEC =
      DeviceSpec.newBuilder()
          .setAndroidPhoneSpec(
              AndroidPhoneSpec.newBuilder()
                  .setBrand("Google")
                  .setNeedDisablePackageCache(false)
                  .build())
          .build();
  private static final DeviceConfig DEVICE_CONFIG_FOR_INSTALL_MAINLINE =
      DeviceConfig.newBuilder()
          .setDeviceSpec(DEVICE_SPEC)
          .setExtension(
              InstallMainlineSpec.installMainlineSpec,
              InstallMainlineSpec.newBuilder().setCleanUpSessions(true).build())
          .build();

  private static final DeviceConfig DEVICE_CONFIG_FOR_RESET =
      DeviceConfig.newBuilder()
          .setDeviceSpec(DEVICE_SPEC)
          .setExtension(
              ResetSpec.resetSpec,
              ResetSpec.newBuilder().setResetOption(ResetOption.TEST_HARNESS).build())
          .build();
  private static final Unary INSTALL_MAINLINE =
      Unary.newBuilder().setFirst(DEVICE_1).setExtension(InstallMainlineSpec.ext, FROM_CMD).build();

  @Test
  public void getDeviceWrapperMap_noOptions() throws Exception {
    assertThat(ProtoHelper.getDeviceWrapperMap(ActionSpec.newBuilder().setNullary(NULLARY).build()))
        .isEmpty();
    assertThat(
            ProtoHelper.getDeviceWrapperMap(ActionSpec.newBuilder().setUnary(UNARY).build())
                .keySet())
        .containsExactly(DevicePosition.FIRST);
    assertThat(
            ProtoHelper.getDeviceWrapperMap(ActionSpec.newBuilder().setBinary(BINARY).build())
                .keySet())
        .containsExactly(DevicePosition.FIRST, DevicePosition.SECOND);
  }

  @Test
  public void getDeviceWrapperMap_hasOptions() throws Exception {
    ActionOptions actionOptions =
        ActionOptions.builder()
            .setCommand(Command.DEFAULT)
            .setAction(Options.builder().build())
            .setFirstDevice(Options.builder().addKeyValues("not exist", "not used").build())
            .setSecondDevice(Options.builder().addKeyValues(DEVICE_CONFIG_KEY, "file_path").build())
            .build();

    assertThat(
            ProtoHelper.getDeviceWrapperMap(
                ActionSpec.newBuilder().setNullary(NULLARY).build(), actionOptions))
        .isEmpty();
    assertThat(
            ProtoHelper.getDeviceWrapperMap(
                ActionSpec.newBuilder().setUnary(UNARY).build(), actionOptions))
        .containsExactly(DevicePosition.FIRST, DeviceWrapper.create(DEVICE_1, Optional.empty()));
    assertThat(
            ProtoHelper.getDeviceWrapperMap(
                ActionSpec.newBuilder().setBinary(BINARY).build(), actionOptions))
        .containsExactly(
            DevicePosition.FIRST,
            DeviceWrapper.create(DEVICE_1, Optional.empty()),
            DevicePosition.SECOND,
            DeviceWrapper.create(DEVICE_2, Optional.of("file_path")));
    DeviceActionException t =
        assertThrows(
            DeviceActionException.class,
            () ->
                ProtoHelper.getDeviceWrapperMap(
                    ActionSpec.newBuilder().setNullary(NULLARY).build(), /* options= */ null));
    assertThat(t.getErrorId().name()).isEqualTo("ILLEGAL_ARGUMENT");
  }

  @Test
  public void mergeActionSpec_getMergedResult() throws Exception {
    ActionSpec actionSpec =
        ProtoHelper.mergeActionSpec(
            Command.INSTALL_MAINLINE,
            ActionSpec.newBuilder().setUnary(INSTALL_MAINLINE).build(),
            DEVICE_CONFIG);

    assertTrue(actionSpec.hasUnary());
    assertThat(actionSpec.getUnary().getFirst()).isEqualTo(DEVICE_1);
    assertTrue(actionSpec.getUnary().hasExtension(InstallMainlineSpec.ext));
    assertThat(actionSpec.getUnary().getExtension(InstallMainlineSpec.ext))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            InstallMainlineSpec.newBuilder()
                .setCleanUpSessions(true)
                .setDevKeySigned(true)
                .addFiles(GCS_FILE)
                .addFiles(LOCAL_FILE_1)
                .setEnableRollback(true)
                .build());
  }

  @Test
  public void buildProtoByOptions_workForInstallMainlineSpec() throws Exception {
    InstallMainlineSpec spec =
        ProtoHelper.buildProtoByOptions(getOptionsForInstallMainline(), InstallMainlineSpec.class);

    assertThat(spec).ignoringRepeatedFieldOrder().isEqualTo(COMPLETE_INSTALL_MAINLINE);
  }

  @Test
  public void buildProtoByOptions_workForAndroidPhoneSpec() throws Exception {
    Options options =
        Options.builder()
            .addKeyValues("brand", "google")
            .addKeyValues("reboot_await", "PT15M")
            .addKeyValues("testharness_boot_timeout", "PT20S")
            .addTrueBoolOptions("wifi_24_only")
            .build();

    AndroidPhoneSpec spec = ProtoHelper.buildProtoByOptions(options, AndroidPhoneSpec.class);

    assertThat(spec)
        .isEqualTo(
            AndroidPhoneSpec.newBuilder()
                .setBrand("google")
                .setRebootAwait(toProtoDuration(Duration.ofMinutes(15)))
                .setTestharnessBootTimeout(toProtoDuration(Duration.ofSeconds(20)))
                .setWifi24Only(true)
                .build());
  }

  @Test
  public void buildProtoByOptions_workForResetSpec() throws Exception {
    ResetSpec spec = ProtoHelper.buildProtoByOptions(getOptionsForReset(), ResetSpec.class);

    assertThat(spec).isEqualTo(COMPLETE_RESET);
  }

  @Test
  public void getDeviceConfigFromTextProto_getExpectedResultForInstallMainline() throws Exception {
    String textproto = Files.readString(Paths.get(TEST_FILE_PATH));

    DeviceConfig deviceConfig =
        ProtoHelper.getDeviceConfigFromTextproto(textproto, Command.INSTALL_MAINLINE);

    assertThat(deviceConfig).isEqualTo(DEVICE_CONFIG_FOR_INSTALL_MAINLINE);
    assertTrue(
        deviceConfig.getExtension(InstallMainlineSpec.installMainlineSpec).getEnableRollback());
    assertFalse(
        deviceConfig.getExtension(InstallMainlineSpec.installMainlineSpec).getDevKeySigned());
    assertTrue(
        deviceConfig.getExtension(InstallMainlineSpec.installMainlineSpec).getCheckRollback());
  }

  @Test
  public void getDeviceConfigFromTextProto_getExpectedResultForReset() throws Exception {
    String textproto = Files.readString(Paths.get(TEST_FILE_PATH));

    DeviceConfig deviceConfig = ProtoHelper.getDeviceConfigFromTextproto(textproto, Command.RESET);

    assertThat(deviceConfig).isEqualTo(DEVICE_CONFIG_FOR_RESET);
  }

  @Test
  public void getActionSpec_workForInstallMainlineSpec() throws Exception {
    ActionOptions actionOptions =
        ActionOptions.builder()
            .setCommand(Command.INSTALL_MAINLINE)
            .setAction(getOptionsForInstallMainline())
            .setFirstDevice(Options.builder().addKeyValues("serial", ID_1).build())
            .build();

    ActionSpec spec = ProtoHelper.getActionSpec(actionOptions);

    assertThat(spec.getUnary().getExtension(InstallMainlineSpec.ext))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(COMPLETE_INSTALL_MAINLINE);
    assertThat(spec.getUnary().getFirst()).isEqualTo(DEVICE_1);
  }

  @Test
  public void getActionSpec_workForResetSpec() throws Exception {
    ActionOptions actionOptions =
        ActionOptions.builder()
            .setCommand(Command.RESET)
            .setAction(getOptionsForReset())
            .setFirstDevice(Options.builder().addKeyValues("serial", ID_1).build())
            .build();

    ActionSpec spec = ProtoHelper.getActionSpec(actionOptions);

    assertThat(spec.getUnary().getExtension(ResetSpec.ext))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(COMPLETE_RESET);
    assertThat(spec.getUnary().getFirst()).isEqualTo(DEVICE_1);
  }

  @Test
  public void getActionSpec_missingId_throwException() throws Exception {
    ActionOptions actionOptions =
        ActionOptions.builder()
            .setCommand(Command.INSTALL_MAINLINE)
            .setAction(getOptionsForInstallMainline())
            .build();

    assertThrows(DeviceActionException.class, () -> ProtoHelper.getActionSpec(actionOptions));
  }

  private static Options getOptionsForInstallMainline() throws Exception {
    return Options.builder()
        .addTrueBoolOptions("enable_rollback")
        .addFalseBoolOptions("clean_up_sessions")
        .addKeyValues("not exist", "not used")
        .addFileOptions("mainline_modules", "/local/path/f1", "/local/path/f2")
        .addFileOptions("train_folder", "gcs:fake project&gs://bucket/d1/o1")
        .build();
  }

  private static Options getOptionsForReset() throws Exception {
    return Options.builder()
        .addTrueBoolOptions("need_preload_modules_recovery")
        .addKeyValues("not exist", "not used")
        .addFileOptions("recovery_modules", "/local/path/f1", "/local/path/f2")
        .addKeyValues("reset_option", "TEST_HARNESS")
        .build();
  }
}
