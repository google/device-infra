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

package com.google.devtools.mobileharness.fe.v6.service.search.pull;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetRawData;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigResult;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link AtsOneFleetDataSource}. */
@RunWith(JUnit4.class)
public final class AtsOneFleetDataSourceTest {

  private static final LabQueryResult TWO_DEVICE_RESULT = labQueryResult("device-0", "device-1");

  private final FakeLabInfoProvider labInfoProvider = new FakeLabInfoProvider();
  private final FakeConfigurationProvider configurationProvider = new FakeConfigurationProvider();
  private final AtsOneFleetDataSource source =
      new AtsOneFleetDataSource(
          new LabInfoFleetPuller(labInfoProvider),
          configurationProvider,
          newDirectExecutorService());

  @Test
  public void fleet_isSelf() {
    assertThat(source.fleet()).isEqualTo(Fleet.FLEET_SELF);
  }

  @Test
  public void pull_populatesWifiSsidFromDeviceConfigs() throws Exception {
    labInfoProvider.setResult(TWO_DEVICE_RESULT);
    configurationProvider.setDeviceConfigs(
        ImmutableList.of(
            deviceConfigWithWifi("device-0", "guest-net"),
            deviceConfigWithWifi("device-1", "lab-net")));

    FleetRawData rawData = source.pull().get();

    assertThat(rawData.labData()).isEqualTo(TWO_DEVICE_RESULT);
    assertThat(rawData.deviceEnrichments().keySet()).containsExactly("device-0", "device-1");
    assertThat(rawData.deviceEnrichments().get("device-0").wifiSsid()).hasValue("guest-net");
    assertThat(rawData.deviceEnrichments().get("device-1").wifiSsid()).hasValue("lab-net");
  }

  @Test
  public void pull_dropsDeviceConfigWithNoWifiSsid() throws Exception {
    labInfoProvider.setResult(TWO_DEVICE_RESULT);
    configurationProvider.setDeviceConfigs(
        ImmutableList.of(
            deviceConfigWithWifi("device-0", "guest-net"),
            // device-1 has a config but no default WiFi, so it contributes no enrichment.
            DeviceConfig.newBuilder().setUuid("device-1").build()));

    FleetRawData rawData = source.pull().get();

    assertThat(rawData.deviceEnrichments().keySet()).containsExactly("device-0");
  }

  @Test
  public void pull_configUnavailable_returnsLabDataWithNoEnrichment() throws Exception {
    labInfoProvider.setResult(TWO_DEVICE_RESULT);
    // The config service is gated off, so getDeviceConfigs returns an empty list.
    configurationProvider.setDeviceConfigs(ImmutableList.of());

    FleetRawData rawData = source.pull().get();

    assertThat(rawData.labData()).isEqualTo(TWO_DEVICE_RESULT);
    assertThat(rawData.deviceEnrichments()).isEmpty();
  }

  private static DeviceConfig deviceConfigWithWifi(String deviceId, String ssid) {
    return DeviceConfig.newBuilder()
        .setUuid(deviceId)
        .setBasicConfig(
            BasicDeviceConfig.newBuilder().setDefaultWifi(WifiConfig.newBuilder().setSsid(ssid)))
        .build();
  }

  private static LabQueryResult labQueryResult(String... deviceIds) {
    DeviceList.Builder deviceList = DeviceList.newBuilder();
    for (String deviceId : deviceIds) {
      deviceList.addDeviceInfo(
          DeviceInfo.newBuilder().setDeviceLocator(DeviceLocator.newBuilder().setId(deviceId)));
    }
    return LabQueryResult.newBuilder()
        .setLabView(
            LabQueryResult.LabView.newBuilder()
                .addLabData(LabData.newBuilder().setDeviceList(deviceList)))
        .build();
  }

  /** In-memory {@link LabInfoProvider} that returns a configured result. */
  private static final class FakeLabInfoProvider implements LabInfoProvider {
    private LabQueryResult result = LabQueryResult.getDefaultInstance();

    void setResult(LabQueryResult result) {
      this.result = result;
    }

    @Override
    public ListenableFuture<GetLabInfoResponse> getLabInfoAsync(
        GetLabInfoRequest request, UniverseScope universe) {
      return immediateFuture(GetLabInfoResponse.newBuilder().setLabQueryResult(result).build());
    }
  }

  /** In-memory {@link ConfigurationProvider} that returns a configured device config list. */
  private static final class FakeConfigurationProvider implements ConfigurationProvider {
    private List<DeviceConfig> deviceConfigs = ImmutableList.of();

    void setDeviceConfigs(List<DeviceConfig> deviceConfigs) {
      this.deviceConfigs = deviceConfigs;
    }

    @Override
    public ListenableFuture<List<DeviceConfig>> getDeviceConfigs(
        List<String> deviceIds, UniverseScope universe) {
      return immediateFuture(deviceConfigs);
    }

    @Override
    public ListenableFuture<ConfigResult<DeviceConfig>> getDeviceConfig(
        String deviceId, UniverseScope universe) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ListenableFuture<ConfigResult<LabConfig>> getLabConfig(
        String hostName, UniverseScope universe) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ListenableFuture<Void> updateDeviceConfig(
        String deviceId, DeviceConfig deviceConfig, UniverseScope universe) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ListenableFuture<Void> updateLabConfig(
        String hostName, LabConfig labConfig, UniverseScope universe) {
      throw new UnsupportedOperationException();
    }
  }
}
