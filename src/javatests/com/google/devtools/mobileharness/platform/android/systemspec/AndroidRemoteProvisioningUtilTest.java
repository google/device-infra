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

package com.google.devtools.mobileharness.platform.android.systemspec;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.shared.util.cbor.CborCsrVerifier;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.testing.FakeCommandResult;
import java.util.Base64;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AndroidRemoteProvisioningUtilTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private Adb adb;
  @Mock private AndroidAdbUtil androidAdbUtil;
  @Mock private AndroidFileUtil androidFileUtil;
  @Mock private CborCsrVerifier cborCsrVerifier;

  private AndroidRemoteProvisioningUtil util;

  @Before
  public void setUp() {
    util = new AndroidRemoteProvisioningUtil(adb, androidAdbUtil, androidFileUtil, cborCsrVerifier);
    when(adb.getAdbCommand()).thenReturn(Command.of("adb"));
  }

  @Test
  public void getInstanceNameToCsr_success() throws Exception {
    String deviceId = "device_id";

    // Mock supportsMicrodroid preconditions for AVF instance.
    when(androidAdbUtil.getProperty(deviceId, AndroidProperty.ABI)).thenReturn("arm64-v8a");
    when(androidAdbUtil.getProperty(
            deviceId, ImmutableList.of("ro.boot.hypervisor.protected_vm.supported")))
        .thenReturn("true");
    when(androidFileUtil.isFileOrDirExisted(deviceId, "/apex/com.android.virt")).thenReturn(true);
    when(androidAdbUtil.getProperty(deviceId, ImmutableList.of("avf.remote_attestation.enabled")))
        .thenReturn("true");

    // Mock adb run
    byte[] dummyCsrBytes = new byte[] {1, 2, 3};
    String dummyCsrBase64 = Base64.getEncoder().encodeToString(dummyCsrBytes);
    when(adb.run(any(Command.class)))
        .thenAnswer(
            invocation -> {
              Command cmd = invocation.getArgument(0);
              String cmdStr = String.join(" ", cmd.getCommand());
              if (cmdStr.contains("remote_provisioning list")) {
                return FakeCommandResult.of("avf\nkeymint", "", 0);
              } else if (cmdStr.contains("remote_provisioning csr")) {
                return FakeCommandResult.of(dummyCsrBase64, "", 0);
              }
              return FakeCommandResult.of("", "", 0);
            });

    Map<String, byte[]> result = util.getInstanceNameToCsr(deviceId);

    assertThat(result.keySet()).containsExactly("avf", "keymint");
    assertThat(result.get("avf")).isEqualTo(dummyCsrBytes);
    assertThat(result.get("keymint")).isEqualTo(dummyCsrBytes);
    verify(cborCsrVerifier, times(2)).verifyCsrChallenge(eq(dummyCsrBytes), any(byte[].class));
  }

  @Test
  public void getInstanceNameToCsr_skipsAvf_whenAvfRemoteAttestationUnsupported() throws Exception {
    String deviceId = "device_id";

    // Mock AVF remote attestation unsupported (protected_vm supported = false).
    when(androidAdbUtil.getProperty(deviceId, AndroidProperty.ABI)).thenReturn("arm64-v8a");
    when(androidAdbUtil.getProperty(
            deviceId, ImmutableList.of("ro.boot.hypervisor.protected_vm.supported")))
        .thenReturn("false");
    when(androidFileUtil.isFileOrDirExisted(deviceId, "/apex/com.android.virt")).thenReturn(true);

    byte[] dummyCsrBytes = new byte[] {1, 2, 3};
    String dummyCsrBase64 = Base64.getEncoder().encodeToString(dummyCsrBytes);
    when(adb.run(any(Command.class)))
        .thenAnswer(
            invocation -> {
              Command cmd = invocation.getArgument(0);
              String cmdStr = String.join(" ", cmd.getCommand());
              if (cmdStr.contains("remote_provisioning list")) {
                return FakeCommandResult.of("avf\nkeymint", "", 0);
              } else if (cmdStr.contains("remote_provisioning csr")) {
                return FakeCommandResult.of(dummyCsrBase64, "", 0);
              }
              return FakeCommandResult.of("", "", 0);
            });

    Map<String, byte[]> result = util.getInstanceNameToCsr(deviceId);

    assertThat(result.keySet()).containsExactly("keymint");
    assertThat(result.get("keymint")).isEqualTo(dummyCsrBytes);
  }

  @Test
  public void getInstanceNameToCsr_throwsException_whenVerifierThrows() throws Exception {
    String deviceId = "device_id";

    when(androidAdbUtil.getProperty(deviceId, AndroidProperty.ABI)).thenReturn("arm64-v8a");
    when(androidFileUtil.isFileOrDirExisted(deviceId, "/apex/com.android.virt")).thenReturn(true);

    byte[] dummyCsrBytes = new byte[] {1, 2, 3};
    String dummyCsrBase64 = Base64.getEncoder().encodeToString(dummyCsrBytes);
    when(adb.run(any(Command.class)))
        .thenAnswer(
            invocation -> {
              Command cmd = invocation.getArgument(0);
              String cmdStr = String.join(" ", cmd.getCommand());
              if (cmdStr.contains("remote_provisioning list")) {
                return FakeCommandResult.of("keymint", "", 0);
              } else if (cmdStr.contains("remote_provisioning csr")) {
                return FakeCommandResult.of(dummyCsrBase64, "", 0);
              }
              return FakeCommandResult.of("", "", 0);
            });

    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_CHALLENGE_MISMATCH,
                "Challenge mismatch"))
        .when(cborCsrVerifier)
        .verifyCsrChallenge(eq(dummyCsrBytes), any(byte[].class));

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> util.getInstanceNameToCsr(deviceId));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_CHALLENGE_MISMATCH);
  }

  @Test
  public void supportsMicrodroid_returnsTrue_underValidConditions() throws Exception {
    String deviceId = "device_id";
    when(androidAdbUtil.getProperty(deviceId, AndroidProperty.ABI)).thenReturn("arm64-v8a");
    when(androidAdbUtil.getProperty(
            deviceId, ImmutableList.of("ro.boot.hypervisor.protected_vm.supported")))
        .thenReturn("true");
    when(androidFileUtil.isFileOrDirExisted(deviceId, "/apex/com.android.virt")).thenReturn(true);

    assertThat(util.supportsMicrodroid(deviceId, true)).isTrue();
  }

  @Test
  public void supportsMicrodroid_returnsFalse_whenAbiUnsupported() throws Exception {
    String deviceId = "device_id";
    when(androidAdbUtil.getProperty(deviceId, AndroidProperty.ABI)).thenReturn("armeabi-v7a");

    assertThat(util.supportsMicrodroid(deviceId, true)).isFalse();
  }

  @Test
  public void supportsMicrodroid_returnsFalse_whenHypervisorUnsupported() throws Exception {
    String deviceId = "device_id";
    when(androidAdbUtil.getProperty(deviceId, AndroidProperty.ABI)).thenReturn("arm64-v8a");
    when(androidAdbUtil.getProperty(
            deviceId, ImmutableList.of("ro.boot.hypervisor.protected_vm.supported")))
        .thenReturn("false");

    assertThat(util.supportsMicrodroid(deviceId, true)).isFalse();
  }

  @Test
  public void supportsMicrodroid_returnsFalse_whenApexVirtMissing() throws Exception {
    String deviceId = "device_id";
    when(androidAdbUtil.getProperty(deviceId, AndroidProperty.ABI)).thenReturn("arm64-v8a");
    when(androidAdbUtil.getProperty(
            deviceId, ImmutableList.of("ro.boot.hypervisor.protected_vm.supported")))
        .thenReturn("true");
    when(androidFileUtil.isFileOrDirExisted(deviceId, "/apex/com.android.virt")).thenReturn(false);

    assertThat(util.supportsMicrodroid(deviceId, true)).isFalse();
  }

  @Test
  public void supportsMicrodroid_hypervisorPropertyValues() throws Exception {
    String deviceId = "device_id";
    when(androidAdbUtil.getProperty(deviceId, AndroidProperty.ABI)).thenReturn("arm64-v8a");
    when(androidFileUtil.isFileOrDirExisted(deviceId, "/apex/com.android.virt")).thenReturn(true);

    // Test true values
    for (String val :
        ImmutableList.of("on", "On", "ON", "true", "True", "TRUE", "yes", "YES", "1", "y", "Y")) {
      when(androidAdbUtil.getProperty(
              deviceId, ImmutableList.of("ro.boot.hypervisor.protected_vm.supported")))
          .thenReturn(val);
      assertThat(util.supportsMicrodroid(deviceId, true)).isTrue();
    }

    // Test true values with whitespaces
    when(androidAdbUtil.getProperty(
            deviceId, ImmutableList.of("ro.boot.hypervisor.protected_vm.supported")))
        .thenReturn("  true  ");
    assertThat(util.supportsMicrodroid(deviceId, true)).isTrue();

    // Test false values
    for (String val :
        ImmutableList.of(
            "off", "Off", "OFF", "false", "False", "FALSE", "no", "NO", "0", "n", "N")) {
      when(androidAdbUtil.getProperty(
              deviceId, ImmutableList.of("ro.boot.hypervisor.protected_vm.supported")))
          .thenReturn(val);
      assertThat(util.supportsMicrodroid(deviceId, true)).isFalse();
    }

    // Test false values with whitespaces
    when(androidAdbUtil.getProperty(
            deviceId, ImmutableList.of("ro.boot.hypervisor.protected_vm.supported")))
        .thenReturn("  false  ");
    assertThat(util.supportsMicrodroid(deviceId, true)).isFalse();

    // Test "invalid" -> false (default value is false)
    when(androidAdbUtil.getProperty(
            deviceId, ImmutableList.of("ro.boot.hypervisor.protected_vm.supported")))
        .thenReturn("invalid");
    assertThat(util.supportsMicrodroid(deviceId, true)).isFalse();
  }

  @Test
  public void supportsMicrodroid_returnsTrue_underValidConditions_nonProtectedVm()
      throws Exception {
    String deviceId = "device_id";
    when(androidAdbUtil.getProperty(deviceId, AndroidProperty.ABI)).thenReturn("arm64-v8a");
    when(androidAdbUtil.getProperty(deviceId, ImmutableList.of("ro.boot.hypervisor.vm.supported")))
        .thenReturn("true");
    when(androidFileUtil.isFileOrDirExisted(deviceId, "/apex/com.android.virt")).thenReturn(true);

    assertThat(util.supportsMicrodroid(deviceId, false)).isTrue();
  }

  @Test
  public void supportsMicrodroid_returnsFalse_whenHypervisorUnsupported_nonProtectedVm()
      throws Exception {
    String deviceId = "device_id";
    when(androidAdbUtil.getProperty(deviceId, AndroidProperty.ABI)).thenReturn("arm64-v8a");
    when(androidAdbUtil.getProperty(deviceId, ImmutableList.of("ro.boot.hypervisor.vm.supported")))
        .thenReturn("false");
    when(androidFileUtil.isFileOrDirExisted(deviceId, "/apex/com.android.virt")).thenReturn(true);

    assertThat(util.supportsMicrodroid(deviceId, false)).isFalse();
  }

  @Test
  public void getInstanceNameToCsr_avfRemoteAttestationPropertyValues() throws Exception {
    String deviceId = "device_id";
    when(androidAdbUtil.getProperty(deviceId, AndroidProperty.ABI)).thenReturn("arm64-v8a");
    when(androidAdbUtil.getProperty(
            deviceId, ImmutableList.of("ro.boot.hypervisor.protected_vm.supported")))
        .thenReturn("true");
    when(androidFileUtil.isFileOrDirExisted(deviceId, "/apex/com.android.virt")).thenReturn(true);

    byte[] dummyCsrBytes = new byte[] {1, 2, 3};
    String dummyCsrBase64 = Base64.getEncoder().encodeToString(dummyCsrBytes);
    when(adb.run(any(Command.class)))
        .thenAnswer(
            invocation -> {
              Command cmd = invocation.getArgument(0);
              String cmdStr = String.join(" ", cmd.getCommand());
              if (cmdStr.contains("remote_provisioning list")) {
                return FakeCommandResult.of("avf", "", 0);
              } else if (cmdStr.contains("remote_provisioning csr")) {
                return FakeCommandResult.of(dummyCsrBase64, "", 0);
              }
              return FakeCommandResult.of("", "", 0);
            });

    // Test false values (avf should be skipped)
    for (String val :
        ImmutableList.of(
            "off", "Off", "OFF", "false", "False", "FALSE", "no", "NO", "0", "n", "N")) {
      when(androidAdbUtil.getProperty(deviceId, ImmutableList.of("avf.remote_attestation.enabled")))
          .thenReturn(val);
      assertThat(util.getInstanceNameToCsr(deviceId)).doesNotContainKey("avf");
    }

    // Test false values with whitespaces
    when(androidAdbUtil.getProperty(deviceId, ImmutableList.of("avf.remote_attestation.enabled")))
        .thenReturn("  false  ");
    assertThat(util.getInstanceNameToCsr(deviceId)).doesNotContainKey("avf");

    // Test true values (avf should NOT be skipped)
    for (String val :
        ImmutableList.of("on", "On", "ON", "true", "True", "TRUE", "yes", "YES", "1", "y", "Y")) {
      when(androidAdbUtil.getProperty(deviceId, ImmutableList.of("avf.remote_attestation.enabled")))
          .thenReturn(val);
      assertThat(util.getInstanceNameToCsr(deviceId)).containsKey("avf");
    }

    // Test true values with whitespaces
    when(androidAdbUtil.getProperty(deviceId, ImmutableList.of("avf.remote_attestation.enabled")))
        .thenReturn("  true  ");
    assertThat(util.getInstanceNameToCsr(deviceId)).containsKey("avf");

    // Test "invalid" -> true (default is true, so avf should NOT be skipped)
    when(androidAdbUtil.getProperty(deviceId, ImmutableList.of("avf.remote_attestation.enabled")))
        .thenReturn("invalid");
    assertThat(util.getInstanceNameToCsr(deviceId)).containsKey("avf");
  }
}
