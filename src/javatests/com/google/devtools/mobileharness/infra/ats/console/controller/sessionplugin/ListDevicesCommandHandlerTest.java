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

package com.google.devtools.mobileharness.infra.ats.console.controller.sessionplugin;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.DeviceDescriptorProto.DeviceDescriptor;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Success;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.ListDevicesCommand;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.Dimension;
import java.time.Clock;
import java.time.Instant;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class ListDevicesCommandHandlerTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Bind @Mock private DeviceQuerier deviceQuerier;
  @Bind @Mock private AndroidAdbUtil androidAdbUtil;
  @Bind @Mock private AndroidAdbInternalUtil androidAdbInternalUtil;
  @Bind @Mock private AndroidSystemSettingUtil androidSystemSettingUtil;
  @Bind private ListeningExecutorService listeningExecutorService;
  @Bind @Mock Clock clock;

  @Inject private ListDevicesCommandHandler listDevicesCommandHandler;

  @Before
  public void setUp() {
    listeningExecutorService =
        ThreadPools.createStandardThreadPool("list-devices-command-handler-test");
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    listDevicesCommandHandler = spy(listDevicesCommandHandler);
  }

  @Test
  public void handle() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("abc")
                        .setStatus("idle")
                        .addType("AndroidOnlineDevice")
                        .addDimension(
                            Dimension.newBuilder()
                                .setName(Name.BATTERY_LEVEL.lowerCaseName())
                                .setValue("100")))
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("def")
                        .setStatus("idle")
                        .addType("AndroidOnlineDevice")
                        .addDimension(
                            Dimension.newBuilder()
                                .setName(Name.BATTERY_LEVEL.lowerCaseName())
                                .setValue("100")))
                .build());
    when(androidAdbInternalUtil.getDeviceSerialsAsMap(any()))
        .thenReturn(ImmutableMap.of("abc", DeviceState.DEVICE));
    when(androidAdbUtil.getProperty(eq("abc"), any(AndroidProperty.class))).thenReturn("n/a");
    Instant now = Instant.now();
    when(clock.instant()).thenReturn(now);

    assertThat(listDevicesCommandHandler.handle(ListDevicesCommand.getDefaultInstance()))
        .isEqualTo(
            AtsSessionPluginOutput.newBuilder()
                .setSuccess(
                    Success.newBuilder()
                        .setOutputMessage(
                            "Serial  State   Allocation  Product  Variant  Build  Battery\n"
                                + "abc     ONLINE  Available   n/a      n/a      n/a    100"))
                .build());
  }

  @Test
  public void convertDeviceInfo_success() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setId("abc")
            .setStatus("idle")
            .addType("AndroidOnlineDevice")
            .addDimension(
                Dimension.newBuilder().setName(Name.BATTERY_LEVEL.lowerCaseName()).setValue("100"))
            .build();
    DeviceDescriptor deviceDescriptorFromDeviceInfo =
        DeviceDescriptor.newBuilder()
            .setSerial("abc")
            .setDeviceState("ONLINE")
            .setAllocationState("Available")
            .setBatteryLevel("100")
            .setProduct("n/a")
            .setProductVariant("n/a")
            .setBuildId("n/a")
            .setDeviceClass("n/a")
            .setTestDeviceState("ONLINE")
            .build();
    DeviceDescriptor deviceDescriptorFromAdb =
        DeviceDescriptor.newBuilder()
            .setSerial("abc")
            .setDeviceState("ONLIE")
            .setAllocationState("n/a")
            .setBatteryLevel("100")
            .setProduct("oriole")
            .setProductVariant("oriole")
            .setBuildId("build")
            .setDeviceClass("n/a")
            .setTestDeviceState("n/a")
            .build();
    DeviceDescriptor combinedDeviceDescriptor =
        DeviceDescriptor.newBuilder()
            .setSerial("abc")
            .setDeviceState("ONLINE")
            .setAllocationState("Available")
            .setBatteryLevel("100")
            .setProduct("oriole")
            .setProductVariant("oriole")
            .setBuildId("build")
            .setDeviceClass("n/a")
            .setTestDeviceState("ONLINE")
            .build();

    assertThat(ListDevicesCommandHandler.convertDeviceInfo(deviceInfo, null))
        .isEqualTo(deviceDescriptorFromDeviceInfo);

    assertThat(ListDevicesCommandHandler.convertDeviceInfo(deviceInfo, deviceDescriptorFromAdb))
        .isEqualTo(combinedDeviceDescriptor);
  }
}
