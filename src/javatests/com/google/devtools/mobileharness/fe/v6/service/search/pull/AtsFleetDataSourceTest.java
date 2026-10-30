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
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.index.CoreFleetRawData;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsHostKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigResult;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.DeviceDimensionSummary;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetAllDeviceDimensionsRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetAllDeviceDimensionsResponse;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link AtsFleetDataSource}. */
@RunWith(JUnit4.class)
public final class AtsFleetDataSourceTest {

  private static final LabQueryResult TWO_DEVICE_RESULT = labQueryResult("device-0", "device-1");

  private final FakeLabInfoProvider labInfoProvider = new FakeLabInfoProvider();
  private final FakeConfigurationProvider configurationProvider = new FakeConfigurationProvider();
  private final AtsFleetDataSource source =
      new AtsFleetDataSource(
          new LabInfoFleetPuller(labInfoProvider),
          new AtsDeviceKeyRegistry(),
          new AtsHostKeyRegistry(),
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

    CoreFleetRawData rawData = source.pull().get();

    assertThat(rawData.labData()).isEqualTo(TWO_DEVICE_RESULT);
    assertThat(rawData.deviceEnrichments().keySet()).containsExactly("device-0", "device-1");
    assertThat(rawData.deviceEnrichments().get("device-0").wifiSsid()).hasValue("guest-net");
    assertThat(rawData.deviceEnrichments().get("device-1").wifiSsid()).hasValue("lab-net");
  }

  @Test
  public void pull_hostWifiConfigOverridesDeviceDefaultWifiAndEnrichesUnconfiguredDevices()
      throws Exception {
    labInfoProvider.setResult(hostedLabQueryResult("host-a", "device-0", "device-1"));
    configurationProvider.putLabConfig(
        "host-a",
        LabConfig.newBuilder()
            .setHostName("host-a")
            .setWifiConfig(WifiConfig.newBuilder().setSsid("ats-host-wifi"))
            .build());
    configurationProvider.setDeviceConfigs(
        ImmutableList.of(deviceConfigWithWifi("device-0", "stale-device-wifi")));

    CoreFleetRawData rawData = source.pull().get();

    assertThat(rawData.deviceEnrichments().keySet()).containsExactly("device-0", "device-1");
    assertThat(rawData.deviceEnrichments().get("device-0").wifiSsid()).hasValue("ats-host-wifi");
    assertThat(rawData.deviceEnrichments().get("device-1").wifiSsid()).hasValue("ats-host-wifi");
  }

  @Test
  @SuppressWarnings("deprecation") // Exercises fallback to BasicDeviceConfig.default_wifi.
  public void pull_sharedHostModeUsesLegacyHostDefaultWifiWhenWifiConfigUnset() throws Exception {
    labInfoProvider.setResult(hostedLabQueryResult("host-shared", "device-0"));
    configurationProvider.putLabConfig(
        "host-shared",
        LabConfig.newBuilder()
            .setHostName("host-shared")
            .setHostProperties(
                HostProperties.newBuilder()
                    .addHostProperty(
                        HostProperty.newBuilder().setKey("device_config_mode").setValue("host")))
            .setDefaultDeviceConfig(
                BasicDeviceConfig.newBuilder()
                    .setDefaultWifi(WifiConfig.newBuilder().setSsid("shared-host-wifi")))
            .build());
    configurationProvider.setDeviceConfigs(
        ImmutableList.of(deviceConfigWithWifi("device-0", "ignored-device-wifi")));

    CoreFleetRawData rawData = source.pull().get();

    assertThat(rawData.deviceEnrichments().get("device-0").wifiSsid()).hasValue("shared-host-wifi");
  }

  @Test
  public void pull_labConfigFailureFallsBackToDeviceConfig() throws Exception {
    labInfoProvider.setResult(hostedLabQueryResult("host-broken", "device-0"));
    configurationProvider.failHost("host-broken");
    configurationProvider.setDeviceConfigs(
        ImmutableList.of(deviceConfigWithWifi("device-0", "device-fallback-wifi")));

    CoreFleetRawData rawData = source.pull().get();

    assertThat(rawData.deviceEnrichments().get("device-0").wifiSsid())
        .hasValue("device-fallback-wifi");
  }

  @Test
  public void pull_dropsDeviceConfigWithNoWifiSsid() throws Exception {
    labInfoProvider.setResult(TWO_DEVICE_RESULT);
    configurationProvider.setDeviceConfigs(
        ImmutableList.of(
            deviceConfigWithWifi("device-0", "guest-net"),
            // device-1 has a config but no default WiFi, so it contributes no enrichment.
            DeviceConfig.newBuilder().setUuid("device-1").build()));

    CoreFleetRawData rawData = source.pull().get();

    assertThat(rawData.deviceEnrichments().keySet()).containsExactly("device-0");
  }

  @Test
  public void pull_configUnavailable_returnsLabDataWithNoEnrichment() throws Exception {
    labInfoProvider.setResult(TWO_DEVICE_RESULT);
    // The config service is gated off, so getDeviceConfigs returns an empty list.
    configurationProvider.setDeviceConfigs(ImmutableList.of());

    CoreFleetRawData rawData = source.pull().get();

    assertThat(rawData.labData()).isEqualTo(TWO_DEVICE_RESULT);
    assertThat(rawData.deviceEnrichments()).isEmpty();
  }

  @Test
  public void pullDimensionCounts_delegatesToLabInfoFleetPuller() throws Exception {
    labInfoProvider.setDimensionsResponse(
        GetAllDeviceDimensionsResponse.newBuilder()
            .addDimensions(DeviceDimensionSummary.newBuilder().setName("model").setDeviceCount(12))
            .addDimensions(DeviceDimensionSummary.newBuilder().setName("pool").setDeviceCount(8))
            .build());

    assertThat(source.pullDimensionCounts().get()).containsExactly("model", 12, "pool", 8);
  }

  @Test
  public void pullDimensions_delegatesBatchAndSingleDimensionPulls() throws Exception {
    labInfoProvider.setResult(
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .addLabData(
                        LabData.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .addDeviceInfo(
                                        DeviceInfo.newBuilder()
                                            .setDeviceLocator(
                                                DeviceLocator.newBuilder().setId("device-0"))
                                            .setDeviceFeature(
                                                DeviceFeature.newBuilder()
                                                    .setCompositeDimension(
                                                        DeviceCompositeDimension.newBuilder()
                                                            .addSupportedDimension(
                                                                DeviceDimension.newBuilder()
                                                                    .setName("carrier")
                                                                    .setValue("Verizon"))
                                                            .addSupportedDimension(
                                                                DeviceDimension.newBuilder()
                                                                    .setName("os_version")
                                                                    .setValue("16"))))))))
            .build());
    AtsDeviceKeyRegistry registry = new AtsDeviceKeyRegistry();
    DeviceKeyDescriptor carrierKey = registry.createLongTailDimensionKey("carrier").get();
    DeviceKeyDescriptor osVersionKey = registry.createLongTailDimensionKey("os_version").get();

    assertThat(source.pullDimension(carrierKey).get().uuidToValues())
        .containsExactly("device-0", ImmutableList.of("Verizon"));
    ImmutableMap<String, DimensionOverlayRaw> batch =
        source.pullDimensions(ImmutableSet.of(carrierKey, osVersionKey)).get();
    assertThat(batch.get("dimension::carrier").uuidToValues())
        .containsExactly("device-0", ImmutableList.of("Verizon"));
    assertThat(batch.get("dimension::os_version").uuidToValues())
        .containsExactly("device-0", ImmutableList.of("16"));
  }

  @SuppressWarnings("deprecation") // Populates legacy BasicDeviceConfig.default_wifi fixture.
  private static DeviceConfig deviceConfigWithWifi(String deviceId, String ssid) {
    return DeviceConfig.newBuilder()
        .setUuid(deviceId)
        .setBasicConfig(
            BasicDeviceConfig.newBuilder().setDefaultWifi(WifiConfig.newBuilder().setSsid(ssid)))
        .build();
  }

  private static LabQueryResult labQueryResult(String... deviceIds) {
    return hostedLabQueryResult("", deviceIds);
  }

  private static LabQueryResult hostedLabQueryResult(String hostName, String... deviceIds) {
    DeviceList.Builder deviceList = DeviceList.newBuilder();
    for (String deviceId : deviceIds) {
      deviceList.addDeviceInfo(
          DeviceInfo.newBuilder().setDeviceLocator(DeviceLocator.newBuilder().setId(deviceId)));
    }
    LabData.Builder labData = LabData.newBuilder().setDeviceList(deviceList);
    if (!hostName.isEmpty()) {
      labData.setLabInfo(
          LabInfo.newBuilder().setLabLocator(LabLocator.newBuilder().setHostName(hostName)));
    }
    return LabQueryResult.newBuilder()
        .setLabView(LabQueryResult.LabView.newBuilder().addLabData(labData))
        .build();
  }

  /** In-memory {@link LabInfoProvider} that returns a configured result. */
  private static final class FakeLabInfoProvider implements LabInfoProvider {
    private LabQueryResult result = LabQueryResult.getDefaultInstance();
    private GetAllDeviceDimensionsResponse dimensionsResponse =
        GetAllDeviceDimensionsResponse.getDefaultInstance();

    void setResult(LabQueryResult result) {
      this.result = result;
    }

    void setDimensionsResponse(GetAllDeviceDimensionsResponse dimensionsResponse) {
      this.dimensionsResponse = dimensionsResponse;
    }

    @Override
    public ListenableFuture<GetLabInfoResponse> getLabInfoAsync(
        GetLabInfoRequest request, UniverseScope universe) {
      return immediateFuture(GetLabInfoResponse.newBuilder().setLabQueryResult(result).build());
    }

    @Override
    public ListenableFuture<GetAllDeviceDimensionsResponse> getAllDeviceDimensionsAsync(
        GetAllDeviceDimensionsRequest request, UniverseScope universe) {
      return immediateFuture(dimensionsResponse);
    }
  }

  /** In-memory {@link ConfigurationProvider} that returns configured lab and device configs. */
  private static final class FakeConfigurationProvider implements ConfigurationProvider {
    private List<DeviceConfig> deviceConfigs = ImmutableList.of();
    private final Map<String, LabConfig> labConfigs = new HashMap<>();
    private final Set<String> failingHosts = new HashSet<>();

    void setDeviceConfigs(List<DeviceConfig> deviceConfigs) {
      this.deviceConfigs = deviceConfigs;
    }

    void putLabConfig(String hostName, LabConfig labConfig) {
      this.labConfigs.put(hostName, labConfig);
    }

    void failHost(String hostName) {
      this.failingHosts.add(hostName);
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
      if (failingHosts.contains(hostName)) {
        return immediateFailedFuture(new RuntimeException("Simulated getLabConfig failure"));
      }
      return immediateFuture(ConfigResult.available(Optional.ofNullable(labConfigs.get(hostName))));
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
