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
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device.PostTestDeviceOp;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.shared.emulator.AdbWebSocketBridge;
import com.google.devtools.mobileharness.platform.android.shared.emulator.AndroidJitEmulatorUtil;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorClient;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorLocalImagePreparer;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.CreateHostRequest;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.Cvd;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.DockerInstance;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.HostInstance;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.api.annotation.FileAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.validator.ValidatorFactory;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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

  @FileAnnotation(
      help = "The host package zip file (cvd-host_package.tar.gz) for the JIT emulator.",
      required = false)
  public static final String FILE_TAG_HOST_IMAGE = "cvd_host_package";

  @FileAnnotation(help = "The device image zip file for the JIT emulator.", required = false)
  public static final String FILE_TAG_DEVICE_IMAGE = "cvd_device_image";

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

  @GuardedBy("cloudOrchestratorLock")
  @Nullable
  private String cvdName;

  @Nullable private AdbWebSocketBridge adbWebSocketBridge;

  private final AdbWebSocketBridgeFactory adbWebSocketBridgeFactory;
  private final LocalFileUtil localFileUtil;

  public AndroidJitEmulator(String deviceId) {
    this(
        deviceId,
        ApiConfig.getInstance(),
        new ValidatorFactory(),
        new AndroidAdbUtil(),
        new CloudOrchestratorClientFactory(),
        new AdbWebSocketBridgeFactory(),
        Sleeper.defaultSleeper());
  }

  @VisibleForTesting
  AndroidJitEmulator(
      String deviceId,
      @Nullable ApiConfig apiConfig,
      @Nullable ValidatorFactory validatorFactory,
      AndroidAdbUtil androidAdbUtil,
      CloudOrchestratorClientFactory cloudOrchestratorClientFactory,
      AdbWebSocketBridgeFactory adbWebSocketBridgeFactory,
      Sleeper sleeper) {
    super(deviceId, apiConfig, validatorFactory);
    this.deviceId = deviceId;
    this.androidAdbUtil = androidAdbUtil;
    this.localFileUtil = new LocalFileUtil();
    this.cloudOrchestratorClientFactory = cloudOrchestratorClientFactory;
    this.adbWebSocketBridgeFactory = adbWebSocketBridgeFactory;
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
    if (Flags.noopJitEmulator.getNonNull()) {
      logger.atInfo().log(
          "JIT emulator %s is no-op. Tradefed will be responsible for actually launching the"
              + " emulator.",
          deviceId);
      return;
    }

    String serviceUrl = Flags.cloudOrchestratorServiceUrl.getNonNull();
    if (serviceUrl.isEmpty()) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_NETWORK_ERROR,
          "cloud_orchestrator_service_url flag is required for JIT emulator setup.");
    }
    // TODO: Remove this lock once we find better way of managing concurrent hosts
    // creation through the Cloud Orchestrator API.
    logger.atInfo().log("Waiting for Cloud Orchestrator lock...");
    cloudOrchestratorLock.lockInterruptibly();
    try {
      logger.atInfo().log("Acquired Cloud Orchestrator lock.");
      createCvdWithClient(serviceUrl, testInfo);
    } catch (MobileHarnessException | InterruptedException | RuntimeException e) {
      logger.atWarning().log("Failed to set up JIT emulator. Cleaning up...");
      stopWebSocketBridgeAndDisconnectAdb();
      fetchLogsWithClient(serviceUrl, testInfo);
      deleteCvdWithClient(serviceUrl);
      throw e;
    } finally {
      cloudOrchestratorLock.unlock();
    }
  }

  @GuardedBy("cloudOrchestratorLock")
  private void createCvdWithClient(String serviceUrl, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    String zone = Flags.cloudOrchestratorZone.getNonNull();
    CloudOrchestratorClient client = cloudOrchestratorClientFactory.create(serviceUrl, "v1", zone);
    client.setBasicAuth("user", "");

    int port = AndroidJitEmulatorUtil.getPortFromDeviceId(deviceId);
    String cvdId = "cvd-" + port;

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

    this.cvdHostId = hostId;
    this.cvdGroup = cvdId;
    this.cvdName = cvdId;

    String branch = testInfo.jobInfo().params().get(PARAM_BRANCH, "aosp-android-latest-release");
    String target =
        testInfo.jobInfo().params().get(PARAM_TARGET, "aosp_cf_x86_64_only_phone-userdebug");
    String buildId = testInfo.jobInfo().params().get(PARAM_BUILD_ID, "");

    String hostImagePath =
        Optional.ofNullable(testInfo.jobInfo().files().get(FILE_TAG_HOST_IMAGE))
            .flatMap(files -> files.stream().findFirst())
            .orElse("");
    String deviceImagePath =
        Optional.ofNullable(testInfo.jobInfo().files().get(FILE_TAG_DEVICE_IMAGE))
            .flatMap(files -> files.stream().findFirst())
            .orElse("");

    if (hostImagePath.isEmpty() != deviceImagePath.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_JIT_EMULATOR_INVALID_PARAMETER_ERROR,
          String.format(
              "Both %s and %s must be provided together. Got host_image: %s, device_image: %s",
              FILE_TAG_HOST_IMAGE, FILE_TAG_DEVICE_IMAGE, hostImagePath, deviceImagePath));
    }

    Cvd cvd;
    if (!hostImagePath.isEmpty() && !deviceImagePath.isEmpty()) {
      logger.atInfo().log("Using local images to launch AVD.");
      CloudOrchestratorLocalImagePreparer manager = new CloudOrchestratorLocalImagePreparer(client);
      try {
        CloudOrchestratorLocalImagePreparer.ImagePreparationResult result =
            manager.prepareImagesAndWait(
                hostId, new File(hostImagePath), new File(deviceImagePath));
        cvd =
            client.createCvdWithLocalImageAndWait(
                hostId, cvdId, result.hostImageDirId(), result.deviceImageDirId());
      } catch (IOException e) {
        throw new MobileHarnessException(
            BasicErrorId.LOCAL_FILE_OR_DIR_NOT_FOUND, "Failed to prepare local images", e);
      }
    } else {
      client.fetchArtifactsAndWait(hostId, branch, buildId, target);
      cvd = client.createCvdWithEnvConfigAndWait(hostId, cvdId, branch, buildId, target);
    }

    logger.atInfo().log("Created CVD: %s, ADB Serial: %s", cvd.name, cvd.adbSerial);
    this.cvdGroup = cvd.group;
    this.cvdName = cvd.name;

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
      adbWebSocketBridge = adbWebSocketBridgeFactory.create(wsUrl, "user" + ":", localPort);
      adbWebSocketBridge.start();

      // Wait a bit for bridge to start listening
      sleeper.sleep(Duration.ofSeconds(2));
      androidAdbUtil.connect("127.0.0.1:" + localPort);
    } else if (cvd.adbSerial != null && !cvd.adbSerial.isEmpty()) {
      androidAdbUtil.connect(cvd.adbSerial);
    }
  }

  public void pullCvdLogs(TestInfo testInfo) {
    if (Flags.noopJitEmulator.getNonNull()) {
      logger.atInfo().log("JIT emulator %s is no-op, skipping log fetch.", deviceId);
      return;
    }
    String propertyKey = "cvd_logs_fetched_" + deviceId;
    if (testInfo.properties().getBoolean(propertyKey).orElse(false)) {
      logger.atInfo().log("CVD logs already fetched for %s, skipping.", deviceId);
      return;
    }
    String serviceUrl = Flags.cloudOrchestratorServiceUrl.getNonNull();
    if (serviceUrl.isEmpty()) {
      logger.atWarning().log("cloud_orchestrator_service_url flag is NOT set, skipping log fetch.");
      return;
    }
    logger.atInfo().log("Waiting for Cloud Orchestrator lock for log fetch...");
    try {
      cloudOrchestratorLock.lockInterruptibly();
      try {
        logger.atInfo().log("Acquired Cloud Orchestrator lock for log fetch.");
        fetchLogsWithClient(serviceUrl, testInfo);
        testInfo.properties().add(propertyKey, "true");
      } finally {
        cloudOrchestratorLock.unlock();
      }
    } catch (InterruptedException e) {
      logger.atWarning().withCause(e).log("Interrupted during log fetch lock");
      Thread.currentThread().interrupt();
    }
  }

  @CanIgnoreReturnValue
  @Override
  public PostTestDeviceOp postRunTest(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    if (Flags.noopJitEmulator.getNonNull()) {
      logger.atInfo().log("JIT emulator %s is no-op", deviceId);
      return PostTestDeviceOp.NONE;
    }

    stopWebSocketBridgeAndDisconnectAdb();

    pullCvdLogs(testInfo);

    String serviceUrl = Flags.cloudOrchestratorServiceUrl.getNonNull();
    if (serviceUrl.isEmpty()) {
      logger.atWarning().log(
          "cloud_orchestrator_service_url flag is NOT set, skipping CVD deletion.");
      return PostTestDeviceOp.NONE;
    }

    logger.atInfo().log("Waiting for Cloud Orchestrator lock for cleanup...");
    try {
      cloudOrchestratorLock.lockInterruptibly();
      try {
        logger.atInfo().log("Acquired Cloud Orchestrator lock for cleanup.");
        deleteCvdWithClient(serviceUrl);
      } finally {
        cloudOrchestratorLock.unlock();
      }
    } catch (InterruptedException e) {
      logger.atWarning().withCause(e).log("Interrupted during cleanup lock");
      Thread.currentThread().interrupt();
    }
    return PostTestDeviceOp.NONE;
  }

  @GuardedBy("cloudOrchestratorLock")
  private void fetchLogsWithClient(String serviceUrl, TestInfo testInfo) {
    if (cvdHostId == null || cvdGroup == null || cvdName == null) {
      logger.atWarning().log("Skipping log fetch as cvdHostId, cvdGroup, or cvdName is null");
      return;
    }
    String zone = Flags.cloudOrchestratorZone.getNonNull();
    CloudOrchestratorClient client = cloudOrchestratorClientFactory.create(serviceUrl, "v1", zone);
    client.setBasicAuth("user", "");

    File targetDir;
    try {
      targetDir = new File(testInfo.getGenFileDir(), deviceId + "/cvd_logs");
      localFileUtil.prepareDir(targetDir.getAbsolutePath());
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to prepare gen file dir, skipping log fetch");
      return;
    }

    try {
      client.fetchAllLogs(cvdHostId, cvdGroup, cvdName, targetDir);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to fetch all logs");
    }
  }

  private void stopWebSocketBridgeAndDisconnectAdb() {
    if (adbWebSocketBridge != null) {
      try {
        int localPort = AndroidJitEmulatorUtil.getPortFromDeviceId(deviceId);
        if (localPort > 0) {
          androidAdbUtil.disconnect("127.0.0.1:" + localPort);
        }
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log("Failed to disconnect ADB");
      } catch (InterruptedException e) {
        logger.atWarning().withCause(e).log("Interrupted during ADB disconnection");
        Thread.currentThread().interrupt();
      }

      logger.atInfo().log("Stopping WebSocket bridge");
      adbWebSocketBridge.stop();
      adbWebSocketBridge = null;
    }
  }

  @GuardedBy("cloudOrchestratorLock")
  private void deleteCvdWithClient(String serviceUrl) throws InterruptedException {
    String zone = Flags.cloudOrchestratorZone.getNonNull();
    CloudOrchestratorClient client = cloudOrchestratorClientFactory.create(serviceUrl, "v1", zone);
    client.setBasicAuth("user", "");

    if (cvdHostId != null && cvdGroup != null) {
      try {
        logger.atInfo().log("Deleting CVD %s on host %s", cvdGroup, cvdHostId);
        client.deleteCvdAndWait(cvdHostId, cvdGroup);
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to delete CVD %s on host %s", cvdGroup, cvdHostId);
      }
      return;
    }

    // Fallback: list and delete if we don't have the specific CVD info.
    try {
      List<HostInstance> hosts = client.listHosts();
      if (hosts == null || hosts.isEmpty()) {
        logger.atWarning().log("No hosts found, skipping CVD deletion.");
        return;
      }
      String hostId = hosts.get(0).name;
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

  /** Factory for creating {@link AdbWebSocketBridge}. */
  public static class AdbWebSocketBridgeFactory {
    public AdbWebSocketBridge create(String wsUrl, String auth, int localPort) {
      return new AdbWebSocketBridge(wsUrl, auth, localPort);
    }
  }
}
