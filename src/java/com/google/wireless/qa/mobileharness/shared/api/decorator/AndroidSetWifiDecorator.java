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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.rpc.RpcExceptionWithErrorId;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.api.util.stub.GrpcStubManager;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.DeviceOpsStub;
import com.google.devtools.mobileharness.platform.android.lightning.networkconnector.NetworkConnector;
import com.google.devtools.mobileharness.platform.android.lightning.networkconnector.WifiConnectArgs;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceOpsServ.ConnectToDefaultWifiRequest;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceOpsServ.ConnectToDefaultWifiResponse;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupResult;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.SetupOnlyDecorator;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidSetWifiDecoratorSpec;
import java.time.Duration;

/** Driver decorator for setting Wifi SSID on the device. */
@DecoratorAnnotation(help = "For setting the device wifi ssid before the test is run.")
public class AndroidSetWifiDecorator extends SetupOnlyDecorator
    implements SpecConfigable<AndroidSetWifiDecoratorSpec> {
  /**
   * The waiting time of timeout to connect to the ssid. 5 mins are useful when using 6G WiFi AP
   * because it has multiple frequency bands.
   */
  protected static final Duration TIMEOUT_SSID_CONNECTION_TIME = Duration.ofMinutes(5);

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected final NetworkConnector networkConnector;

  public AndroidSetWifiDecorator(Driver decoratedDriver, TestInfo testInfo) {
    this(decoratedDriver, testInfo, new NetworkConnector());
  }

  @VisibleForTesting
  AndroidSetWifiDecorator(
      Driver decoratedDriver, TestInfo testInfo, NetworkConnector networkConnector) {
    super(decoratedDriver, testInfo);
    this.networkConnector = networkConnector;
  }

  @VisibleForTesting
  DeviceOpsStub getDeviceOpsStub(String labIp, int rpcPort) {
    return GrpcStubManager.getInstance().getDeviceOpsStub(labIp + ":" + rpcPort);
  }

  @Override
  protected SetupResult setUp(SetupContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    Device device = getDevice();
    String deviceId = device.getDeviceId();
    AndroidSetWifiDecoratorSpec spec = testInfo.jobInfo().combinedSpec(this, deviceId);
    String wifiSsid;
    String wifiPsk;
    boolean wifiScanSsid = false;
    int retryNum = spec.getWifiRetryNum();
    boolean wifiSsidOptional = spec.getWifiSsidOptional();
    if (spec.getUseDefaultSsid()) {
      // Direct the lab server to connect to default wifi via gRPC.
      String labIp = "localhost";
      int rpcPort = Flags.rpcPort.getNonNull();
      if (rpcPort > 0 && labIp != null && !labIp.isEmpty()) {
        try {
          DeviceOpsStub stub = getDeviceOpsStub(labIp, rpcPort);
          ConnectToDefaultWifiResponse response =
              stub.connectToDefaultWifi(
                  ConnectToDefaultWifiRequest.newBuilder().setDeviceId(deviceId).build());
          if (response.getSuccess()) {
            return SetupResult.continueDecorated();
          } else {
            throw new MobileHarnessException(
                AndroidErrorId.ANDROID_SET_WIFI_DECORATOR_WIFI_CONNECT_ERROR,
                "Failed to connect to default Wi-Fi: Lab Server returned failure.");
          }
        } catch (RpcExceptionWithErrorId e) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_SET_WIFI_DECORATOR_WIFI_CONNECT_ERROR,
              "Failed to connect to default lab WiFi via gRPC for device " + deviceId,
              e);
        }
      } else {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_SET_WIFI_DECORATOR_WIFI_CONNECT_ERROR,
            "Lab server IP or RPC port is unavailable for use_default_ssid on device " + deviceId);
      }
    } else {
      // Get the wifi config from the spec.
      wifiSsid = spec.getWifiSsid();

      if (Strings.isNullOrEmpty(wifiSsid)) {
        if (!wifiSsidOptional) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_SET_WIFI_DECORATOR_SSID_NOT_PRESENT_ERROR,
              "Failed to get SSID for the device " + deviceId);
        } else {
          testInfo
              .log()
              .atWarning()
              .alsoTo(logger)
              .log(
                  "SSID for the device %s is not present or empty. Skipping wifi setup.", deviceId);
          return SetupResult.continueDecorated();
        }
      }

      wifiPsk = spec.getWifiPsk();
      wifiScanSsid = spec.getWifiScanSsid();
    }
    WifiConnectArgs connectArgs =
        WifiConnectArgs.builder()
            .setWifiSsid(wifiSsid)
            .setWifiPsk(Strings.nullToEmpty(wifiPsk))
            .setScanSsid(wifiScanSsid)
            .setWaitTimeout(TIMEOUT_SSID_CONNECTION_TIME)
            .setRetryNum(retryNum)
            .build();
    networkConnector.connectToWifi(device, connectArgs, testInfo.log());
    return SetupResult.continueDecorated();
  }
}
