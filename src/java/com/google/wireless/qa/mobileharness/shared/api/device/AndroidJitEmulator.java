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

package com.google.wireless.qa.mobileharness.shared.api.device;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device.PostTestDeviceOp;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.shared.emulator.AdbWebSocketBridge;
import com.google.devtools.mobileharness.platform.android.shared.emulator.AndroidJitEmulatorUtil;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorClient;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.CreateHostRequest;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.Cvd;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.DockerInstance;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.HostInstance;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.validator.ValidatorFactory;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * Android emulator device class. This emulator is created by Cloud Orchestrator on demand of test
 * requests. If no-op mode is enabled, the emulator creation is skipped in MobileHarness and assumed
 * to be handled by Tradefed.
 */
public class AndroidJitEmulator extends AndroidDevice {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @ParamAnnotation(required = false, help = "The branch of the Android JIT emulator build.")
  public static final String PARAM_BRANCH = "android_jit_emulator_branch";

  @ParamAnnotation(required = false, help = "The target of the Android JIT emulator build.")
  public static final String PARAM_TARGET = "android_jit_emulator_target";

  @ParamAnnotation(required = false, help = "The build ID of the Android JIT emulator build.")
  public static final String PARAM_BUILD_ID = "android_jit_emulator_build_id";

  // A static lock to ensure only one thread is interacting with Cloud Orchestrator at a time
  // across all instances.
  private static final ReentrantLock cloudOrchestratorLock = new ReentrantLock();

  private final String deviceId;
  private final AndroidAdbUtil androidAdbUtil;
  private final CloudOrchestratorClientFactory cloudOrchestratorClientFactory;
  private final Sleeper sleeper;

  @GuardedBy("cloudOrchestratorLock")
  @Nullable
  private String cvdHostId;

  @GuardedBy("cloudOrchestratorLock")
  @Nullable
  private String cvdGroup;

  @Nullable private AdbWebSocketBridge adbWebSocketBridge;

  public AndroidJitEmulator(String deviceId) {
    this(
        deviceId,
        ApiConfig.getInstance(),
        new ValidatorFactory(),
        new AndroidAdbUtil(),
        new CloudOrchestratorClientFactory(),
        Sleeper.defaultSleeper());
  }

  @VisibleForTesting
  AndroidJitEmulator(
      String deviceId,
      @Nullable ApiConfig apiConfig,
      @Nullable ValidatorFactory validatorFactory,
      AndroidAdbUtil androidAdbUtil,
      CloudOrchestratorClientFactory cloudOrchestratorClientFactory,
      Sleeper sleeper) {
    super(deviceId, apiConfig, validatorFactory);
    this.deviceId = deviceId;
    this.androidAdbUtil = androidAdbUtil;
    this.cloudOrchestratorClientFactory = cloudOrchestratorClientFactory;
    this.sleeper = sleeper;
  }

  @Override
  public void prepare() throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("JIT emulator start setup. %s", deviceId);
    addDimension(Ascii.toLowerCase(AndroidProperty.ABILIST.name()), "x86_64,arm64-v8a");
    addDimension(Ascii.toLowerCase(AndroidProperty.ABI.name()), "arm64-v8a");
    addSupportedDriver("NoOpDriver");
    addSupportedDriver("AndroidInstrumentation");
    addSupportedDriver("MoblyTest");
    addSupportedDriver("TradefedTest");

    addSupportedDecorator("AndroidHdVideoDecorator");
    addSupportedDecorator("AndroidDeviceSettingsDecorator");
    addSupportedDecorator("AndroidAdbShellDecorator");

    addSupportedDeviceType(AndroidJitEmulator.class.getSimpleName());
    addSupportedDeviceType(AndroidDevice.class.getSimpleName());
    basicAndroidDecoratorConfiguration();

    logger.atInfo().log("JIT emulator %s is Ready", deviceId);
  }

  @Override
  public void preRunTest(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    if (Flags.instance().noopJitEmulator.getNonNull()) {
      logger.atInfo().log(
          "JIT emulator %s is no-op. Tradefed will be responsible for actually launching the"
              + " emulator.",
          deviceId);
      return;
    }

    String serviceUrl = Flags.instance().cloudOrchestratorServiceUrl.getNonNull();
    if (serviceUrl.isEmpty()) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_NETWORK_ERROR,
          "cloud_orchestrator_service_url flag is required for JIT emulator setup.");
    }
    // TODO: Remove this lock once we find better way of managing concurrent hosts creation through
    // the Cloud Orchestrator API.
    logger.atInfo().log("Waiting for Cloud Orchestrator lock...");
    cloudOrchestratorLock.lockInterruptibly();
    try {
      logger.atInfo().log("Acquired Cloud Orchestrator lock.");
      createCvdWithClient(serviceUrl, testInfo);
    } finally {
      cloudOrchestratorLock.unlock();
    }
  }

  @GuardedBy("cloudOrchestratorLock")
  private void createCvdWithClient(String serviceUrl, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    String zone = Flags.instance().cloudOrchestratorZone.getNonNull();
    CloudOrchestratorClient client = cloudOrchestratorClientFactory.create(serviceUrl, "v1", zone);
    client.setBasicAuth("user", "");

    int port = AndroidJitEmulatorUtil.getPortFromDeviceId(deviceId);
    String cvdId = "cvd-" + (port > 0 ? port : (deviceId.hashCode() & 0xffff));

    List<HostInstance> hosts = client.listHosts();
    String hostId;

    if (hosts == null || hosts.isEmpty()) {
      logger.atInfo().log("No hosts found, creating a new one...");
      HostInstance hostReq = new HostInstance();
      hostReq.docker = new DockerInstance();
      hostReq.docker.imageName = "cuttlefish-orchestration:latest";

      HostInstance host = client.createHostAndWait(new CreateHostRequest(hostReq));
      hostId = host.name;
    } else {
      hostId = hosts.get(0).name;
    }

    String branch = testInfo.jobInfo().params().get(PARAM_BRANCH, "aosp-android-latest-release");
    String target =
        testInfo.jobInfo().params().get(PARAM_TARGET, "aosp_cf_x86_64_only_phone-userdebug");
    String buildId = testInfo.jobInfo().params().get(PARAM_BUILD_ID, "");

    // TODO: Support user-supplied images to launch AVD.

    // 1. Fetch artifacts
    client.fetchArtifactsAndWait(hostId, branch, buildId, target);

    // 2. Create CVD with env_config
    Cvd cvd = client.createCvdWithEnvConfigAndWait(hostId, cvdId, branch, buildId, target);

    logger.atInfo().log("Created CVD: %s, ADB Serial: %s", cvd.name, cvd.adbSerial);
    this.cvdHostId = hostId;
    this.cvdGroup = cvd.group;

    int localPort = AndroidJitEmulatorUtil.getPortFromDeviceId(deviceId);
    if (localPort > 0) {
      // Build WebSocket URL
      String hostEndpoint = client.getHostEndpoint(hostId);
      String wsUrl =
          hostEndpoint.replace("http://", "ws://").replace("https://", "wss://")
              + "/devices/"
              + (cvd.webrtcDeviceId != null ? cvd.webrtcDeviceId : cvd.name)
              + "/adb";

      logger.atInfo().log("Starting WebSocket bridge on port %d to %s", localPort, wsUrl);
      adbWebSocketBridge = new AdbWebSocketBridge(wsUrl, "user" + ":", localPort);
      adbWebSocketBridge.start();

      // Wait a bit for bridge to start listening
      sleeper.sleep(Duration.ofSeconds(2));
      androidAdbUtil.connect("127.0.0.1:" + localPort);
    } else if (cvd.adbSerial != null && !cvd.adbSerial.isEmpty()) {
      androidAdbUtil.connect(cvd.adbSerial);
    }
  }

  @CanIgnoreReturnValue
  @Override
  public PostTestDeviceOp postRunTest(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    if (Flags.instance().noopJitEmulator.getNonNull()) {
      logger.atInfo().log("JIT emulator %s is no-op", deviceId);
      return PostTestDeviceOp.NONE;
    }

    if (adbWebSocketBridge != null) {
      logger.atInfo().log("Stopping WebSocket bridge");
      adbWebSocketBridge.stop();
      adbWebSocketBridge = null;
      int localPort = AndroidJitEmulatorUtil.getPortFromDeviceId(deviceId);
      if (localPort > 0) {
        try {
          androidAdbUtil.disconnect("127.0.0.1:" + localPort);
        } catch (MobileHarnessException e) {
          logger.atWarning().withCause(e).log("Failed to disconnect ADB");
        }
      }
    }

    String serviceUrl = Flags.instance().cloudOrchestratorServiceUrl.getNonNull();
    if (!serviceUrl.isEmpty()) {
      logger.atInfo().log("Waiting for Cloud Orchestrator lock...");
      cloudOrchestratorLock.lockInterruptibly();
      try {
        logger.atInfo().log("Acquired Cloud Orchestrator lock.");
        deleteCvdWithClient(serviceUrl);
      } finally {
        cloudOrchestratorLock.unlock();
      }
    } else {
      logger.atWarning().log(
          "cloud_orchestrator_service_url flag is NOT set, skipping CVD deletion.");
    }
    return PostTestDeviceOp.NONE;
  }

  @GuardedBy("cloudOrchestratorLock")
  private void deleteCvdWithClient(String serviceUrl)
      throws MobileHarnessException, InterruptedException {
    String zone = Flags.instance().cloudOrchestratorZone.getNonNull();
    CloudOrchestratorClient client = cloudOrchestratorClientFactory.create(serviceUrl, "v1", zone);
    client.setBasicAuth("user", "");

    List<HostInstance> hosts = client.listHosts();
    if (hosts == null || hosts.isEmpty()) {
      logger.atWarning().log("No hosts found, skipping CVD deletion.");
      return;
    }
    String hostId = hosts.get(0).name;

    if (cvdHostId != null && cvdGroup != null) {
      try {
        client.deleteCvdAndWait(cvdHostId, cvdGroup);
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to delete CVD %s on host %s", cvdGroup, cvdHostId);
      }
      return;
    }

    // Fallback: list and delete if we don't have the specific CVD info.
    try {
      List<Cvd> cvds = client.listCvds(hostId);
      if (cvds != null) {
        for (Cvd cvd : cvds) {
          // Simple logic: delete all for now, or match by some ID.
          client.deleteCvdAndWait(hostId, cvd.group);
        }
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to delete CVD with Cloud Orchestrator");
    }
  }

  @Override
  public boolean canReboot() {
    return false;
  }

  @Override
  public void reboot() throws MobileHarnessException, InterruptedException {
    logger.atSevere().log("Unexpected attempt to reboot a non-rebootable device");
  }

  @Override
  public boolean isRooted() {
    //  We suppose all emulators are rooted.
    return true;
  }

  /** Factory for creating {@link CloudOrchestratorClient}. */
  public static class CloudOrchestratorClientFactory {
    public CloudOrchestratorClient create(String serviceUrl, String version, String zone) {
      return new CloudOrchestratorClient(serviceUrl, version, zone);
    }
  }
}
