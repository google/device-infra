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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.shared.util.cbor.CborCsrVerifier;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Utility class for Android Remote Provisioning and microdroid support checks. */
@Singleton
public class AndroidRemoteProvisioningUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String PROP_AVF_REMOTE_ATTESTATION_ENABLED =
      "avf.remote_attestation.enabled";

  private static final ImmutableSet<String> TRUE_VALUES =
      ImmutableSet.of("1", "y", "yes", "on", "true");
  private static final ImmutableSet<String> FALSE_VALUES =
      ImmutableSet.of("0", "n", "no", "off", "false");

  private final Adb adb;
  private final AndroidAdbUtil androidAdbUtil;
  private final AndroidFileUtil androidFileUtil;
  private final CborCsrVerifier cborCsrVerifier;

  @Inject
  AndroidRemoteProvisioningUtil(
      Adb adb,
      AndroidAdbUtil androidAdbUtil,
      AndroidFileUtil androidFileUtil,
      CborCsrVerifier cborCsrVerifier) {
    this.adb = adb;
    this.androidAdbUtil = androidAdbUtil;
    this.androidFileUtil = androidFileUtil;
    this.cborCsrVerifier = cborCsrVerifier;
  }

  /**
   * Gets a map of instance name to CSR bytes.
   *
   * @param deviceId the ID of the device to query
   * @return a map mapping remote provisioning instance names to their base64-decoded CSR bytes
   * @throws MobileHarnessException if query fails or challenge validation fails
   * @throws InterruptedException if the process is interrupted
   */
  public Map<String, byte[]> getInstanceNameToCsr(String deviceId)
      throws MobileHarnessException, InterruptedException {
    LinkedHashMap<String, byte[]> instanceNameToCsr = new LinkedHashMap<>();
    for (String name : getInstanceNames(deviceId)) {
      // TODO: Remove this bypass once the AVF RKP HAL declaration is moved to
      // /vendor.
      if (name.equals("avf") && !supportAvfRemoteAttestation(deviceId)) {
        logger.atInfo().log(
            "Skip AVF instance as protected VM support required to run it is missing");
        continue;
      }
      byte[] csr = getCsr(deviceId, name);
      instanceNameToCsr.putIfAbsent(name, csr);
    }
    return instanceNameToCsr;
  }

  private String[] getInstanceNames(String deviceId)
      throws MobileHarnessException, InterruptedException {
    Command cmd = adb.getAdbCommand().args("-s", deviceId, "shell", "cmd remote_provisioning list");
    CommandResult result = adb.run(cmd);
    if (result.exitCode() != 0) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_DECODE_ERROR,
          String.format("Failed to get instance names: %s", result.stderr()));
    }
    return result.stdout().lines().toArray(String[]::new);
  }

  private byte[] getCsr(String deviceId, String name)
      throws MobileHarnessException, InterruptedException {
    byte[] challenge = new byte[16];
    try {
      SecureRandom.getInstanceStrong().nextBytes(challenge);
    } catch (NoSuchAlgorithmException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_SECURE_RANDOM_ERROR,
          "Failed to get SecureRandom instance",
          e);
    }
    String challengeBase64 = Base64.getEncoder().encodeToString(challenge);
    Command cmd =
        adb.getAdbCommand()
            .args(
                "-s",
                deviceId,
                "shell",
                "cmd remote_provisioning csr --challenge " + challengeBase64 + " " + name);
    CommandResult result = adb.run(cmd);
    if (result.exitCode() != 0) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_DECODE_ERROR,
          String.format(
              "Failed to get csr for instance name: %s due to exception : %s",
              name, result.stderr()));
    }
    // Confirm that the CSR contains the challenge from this request.
    String csrBase64 = result.stdout().trim();
    byte[] csrBytes;
    try {
      csrBytes = Base64.getDecoder().decode(csrBase64);
    } catch (IllegalArgumentException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_DECODE_ERROR,
          "Failed to base64 decode CSR: " + csrBase64,
          e);
    }

    cborCsrVerifier.verifyCsrChallenge(csrBytes, challenge);

    return csrBytes;
  }

  private boolean supportAvfRemoteAttestation(String deviceId)
      throws MobileHarnessException, InterruptedException {
    return supportsMicrodroid(deviceId, /* protectedVm= */ true)
        && getBooleanProperty(deviceId, PROP_AVF_REMOTE_ATTESTATION_ENABLED, true);
  }

  /**
   * Checks if the device supports microdroid.
   *
   * @param deviceId the ID of the device to check
   * @param protectedVm whether to check for protected VM support
   * @return true if microdroid is supported
   * @throws MobileHarnessException if adb query fails
   * @throws InterruptedException if the process is interrupted
   */
  public boolean supportsMicrodroid(String deviceId, boolean protectedVm)
      throws MobileHarnessException, InterruptedException {
    String abi = androidAdbUtil.getProperty(deviceId, AndroidProperty.ABI).trim();
    if (abi.isEmpty() || (!abi.startsWith("arm64") && !abi.startsWith("x86_64"))) {
      logger.atInfo().log("Unsupported ABI for microdroid: %s", abi);
      return false;
    }

    if (protectedVm) {
      boolean pVmSupported =
          getBooleanProperty(deviceId, "ro.boot.hypervisor.protected_vm.supported", false);
      if (!pVmSupported) {
        logger.atInfo().log("Device does not support protected virtual machines.");
        return false;
      }
    } else {
      boolean nonProtectedVmSupported =
          getBooleanProperty(deviceId, "ro.boot.hypervisor.vm.supported", false);
      if (!nonProtectedVmSupported) {
        logger.atInfo().log("Device does not support non protected virtual machines.");
        return false;
      }
    }

    if (!androidFileUtil.isFileOrDirExisted(deviceId, "/apex/com.android.virt")) {
      logger.atInfo().log("com.android.virt APEX was not pre-installed.");
      return false;
    }
    return true;
  }

  private boolean getBooleanProperty(String deviceId, String propertyKey, boolean defaultValue)
      throws MobileHarnessException, InterruptedException {
    String value = androidAdbUtil.getProperty(deviceId, ImmutableList.of(propertyKey)).trim();
    if (value.isEmpty()) {
      return defaultValue;
    }
    String lowercaseValue = value.toLowerCase(Locale.ENGLISH);
    if (TRUE_VALUES.contains(lowercaseValue)) {
      return true;
    }
    if (FALSE_VALUES.contains(lowercaseValue)) {
      return false;
    }
    return defaultValue;
  }
}
