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

package com.google.devtools.mobileharness.platform.android.deviceadmin;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class DeviceAdminUtilTest {
  private static final String DEVICE_ID = "device_id";
  private static final String DEVICE_ADMIN_CLI_PATH = "/path/to/device_admin_cli.jar";
  private static final String KMS_KEY_NAME =
      "projects/foo-project/locations/global/keyRings/foo-keyRing/cryptoKeys/foo-key/cryptoKeyVersions/1";
  private static final String CRED_PATH = "/path/to/cred";
  private static final String ADMIN_APP_PATH = "/path/to/admin_app.apk";
  private static final String JAVA_BIN = "/path/to/java";

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Mock private CommandExecutor commandExecutor;
  @Mock private SystemUtil systemUtil;

  DeviceAdminUtil deviceAdminUtil;

  @Before
  public void setUp() {
    this.deviceAdminUtil =
        new DeviceAdminUtil(
            commandExecutor,
            systemUtil,
            DEVICE_ADMIN_CLI_PATH,
            KMS_KEY_NAME,
            CRED_PATH,
            ADMIN_APP_PATH);
    when(systemUtil.getJavaBin()).thenReturn(JAVA_BIN);
  }

  @Test
  public void install_success_commandExecuted() throws Exception {
    deviceAdminUtil.install(DEVICE_ID);

    verify(commandExecutor)
        .run(
            Command.of(
                JAVA_BIN,
                "-jar",
                DEVICE_ADMIN_CLI_PATH,
                "--action=INSTALL",
                "--serial=" + DEVICE_ID,
                "--kms_key_name=" + KMS_KEY_NAME,
                "--credentials_path=" + CRED_PATH,
                "--admin_app_path=" + ADMIN_APP_PATH));
  }

  @Test
  public void install_commandFail_throwException() throws Exception {
    when(commandExecutor.run(any())).thenThrow(CommandException.class);

    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> deviceAdminUtil.install(DEVICE_ID));
    assertThat(e.getErrorId()).isEqualTo(AndroidErrorId.DEVICE_ADMIN_UTIL_INSTALL_ERROR);
  }

  @Test
  public void lock_success_commandExecuted() throws Exception {
    deviceAdminUtil.lock(DEVICE_ID);

    verify(commandExecutor)
        .run(
            Command.of(
                JAVA_BIN,
                "-jar",
                DEVICE_ADMIN_CLI_PATH,
                "--action=LOCK",
                "--serial=" + DEVICE_ID,
                "--kms_key_name=" + KMS_KEY_NAME,
                "--credentials_path=" + CRED_PATH));
  }

  @Test
  public void lock_commandFail_throwException() throws Exception {
    when(commandExecutor.run(any())).thenThrow(CommandException.class);

    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> deviceAdminUtil.lock(DEVICE_ID));
    assertThat(e.getErrorId()).isEqualTo(AndroidErrorId.DEVICE_ADMIN_UTIL_LOCK_ERROR);
  }

  @Test
  public void unlock_success_commandExecuted() throws Exception {
    deviceAdminUtil.unlock(DEVICE_ID);

    verify(commandExecutor)
        .run(
            Command.of(
                JAVA_BIN,
                "-jar",
                DEVICE_ADMIN_CLI_PATH,
                "--action=UNLOCK",
                "--serial=" + DEVICE_ID,
                "--kms_key_name=" + KMS_KEY_NAME,
                "--credentials_path=" + CRED_PATH,
                "--admin_app_path=" + ADMIN_APP_PATH));
  }

  @Test
  public void unlock_commandFail_throwException() throws Exception {
    when(commandExecutor.run(any())).thenThrow(CommandException.class);

    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> deviceAdminUtil.unlock(DEVICE_ID));
    assertThat(e.getErrorId()).isEqualTo(AndroidErrorId.DEVICE_ADMIN_UTIL_UNLOCK_ERROR);
  }

  @Test
  public void enable_success_commandExecuted() throws Exception {
    deviceAdminUtil.enable(DEVICE_ID);

    verify(commandExecutor)
        .run(
            Command.of(
                JAVA_BIN,
                "-jar",
                DEVICE_ADMIN_CLI_PATH,
                "--action=ENABLE",
                "--serial=" + DEVICE_ID,
                "--kms_key_name=" + KMS_KEY_NAME,
                "--credentials_path=" + CRED_PATH));
  }

  @Test
  public void enable_commandFail_throwException() throws Exception {
    when(commandExecutor.run(any())).thenThrow(CommandException.class);

    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> deviceAdminUtil.enable(DEVICE_ID));
    assertThat(e.getErrorId()).isEqualTo(AndroidErrorId.DEVICE_ADMIN_UTIL_ENABLE_ERROR);
  }

  @Test
  public void setupAndLock_success_commandExecuted() throws Exception {
    deviceAdminUtil.setupAndLock(DEVICE_ID);

    verify(commandExecutor)
        .run(
            Command.of(
                JAVA_BIN,
                "-jar",
                DEVICE_ADMIN_CLI_PATH,
                "--action=INSTALL",
                "--serial=" + DEVICE_ID,
                "--kms_key_name=" + KMS_KEY_NAME,
                "--credentials_path=" + CRED_PATH,
                "--admin_app_path=" + ADMIN_APP_PATH));

    verify(commandExecutor)
        .run(
            Command.of(
                JAVA_BIN,
                "-jar",
                DEVICE_ADMIN_CLI_PATH,
                "--action=ENABLE",
                "--serial=" + DEVICE_ID,
                "--kms_key_name=" + KMS_KEY_NAME,
                "--credentials_path=" + CRED_PATH));

    verify(commandExecutor)
        .run(
            Command.of(
                JAVA_BIN,
                "-jar",
                DEVICE_ADMIN_CLI_PATH,
                "--action=LOCK",
                "--serial=" + DEVICE_ID,
                "--kms_key_name=" + KMS_KEY_NAME,
                "--credentials_path=" + CRED_PATH));
  }

  @Test
  public void toggleRestrictions_eanble_commandExecuted() throws Exception {
    ImmutableList<String> restrictions = ImmutableList.of("restriction1", "restriction2");
    deviceAdminUtil.toggleRestrictions(DEVICE_ID, restrictions, true);

    verify(commandExecutor)
        .run(
            Command.of(
                JAVA_BIN,
                "-jar",
                DEVICE_ADMIN_CLI_PATH,
                "--action=TOGGLE_ON",
                "--serial=" + DEVICE_ID,
                "--kms_key_name=" + KMS_KEY_NAME,
                "--credentials_path=" + CRED_PATH,
                "--restrictions=restriction1,restriction2"));
  }

  @Test
  public void toggleRestrictions_disableFeatures_commandExecuted() throws Exception {
    ImmutableList<String> restrictions = ImmutableList.of("restriction1", "restriction2");
    deviceAdminUtil.toggleRestrictions(DEVICE_ID, restrictions, false);

    verify(commandExecutor)
        .run(
            Command.of(
                JAVA_BIN,
                "-jar",
                DEVICE_ADMIN_CLI_PATH,
                "--action=TOGGLE_OFF",
                "--serial=" + DEVICE_ID,
                "--kms_key_name=" + KMS_KEY_NAME,
                "--credentials_path=" + CRED_PATH,
                "--restrictions=restriction1,restriction2"));
  }
}
