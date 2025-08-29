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

package com.google.devtools.mobileharness.api.devicemanager.proxy;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableListMultimap;
import com.google.devtools.mobileharness.api.model.job.TestLocator;
import com.google.devtools.mobileharness.api.model.job.in.Decorators;
import com.google.devtools.mobileharness.infra.controller.device.provider.deviceapi.LeasedDeviceConnection;
import com.google.devtools.mobileharness.infra.controller.device.provider.deviceapi.RemoteDeviceConnector;
import com.google.devtools.mobileharness.infra.controller.device.proxy.ProxyDeviceRequirement;
import com.google.devtools.mobileharness.infra.controller.scheduler.model.job.in.DeviceRequirement;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.omnilab.device.api.DeviceSpecification;
import com.google.devtools.omnilab.device.api.Dimension;
import com.google.devtools.omnilab.device.api.Dimensions;
import com.google.devtools.omnilab.device.api.UnknownDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.in.ScopedSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AndroidRealDeviceProxyTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  private static final String LOCAL_ADB_SERIAL = "localhost:12345";
  private static final String REMOTE_DEVICE_ID = "remote_device_id";
  private static final String HOSTNAME = "hostname";
  private static final String DECORATOR = "AndroidLogCatDecorator";
  private static final String MODEL = "pixel 6";
  private static final String SDK_VERSION = "30";

  @Mock private TestLocator testLocator;
  @Mock private RemoteDeviceConnector remoteDeviceConnector;
  @Mock private LeasedDeviceConnection leasedDeviceConnection;
  @Mock private AndroidSystemStateUtil androidSystemStateUtil;

  private AndroidRealDeviceProxy androidRealDeviceProxy;

  @Before
  public void setUp() throws Exception {
    Timing timing = new Timing();
    Decorators decorators = new Decorators();
    decorators.add(DECORATOR);
    com.google.devtools.mobileharness.api.model.job.in.Dimensions dimensions =
        new com.google.devtools.mobileharness.api.model.job.in.Dimensions();
    dimensions.add("model", MODEL);
    dimensions.add("sdk_version", SDK_VERSION);
    DeviceRequirement deviceRequirement =
        DeviceRequirement.create("AndroidRealDevice", decorators, dimensions);
    SubDeviceSpec subDeviceSpec =
        SubDeviceSpec.createForTesting(deviceRequirement, new ScopedSpecs(timing), timing);
    SubDeviceSpecs subDeviceSpecs = new SubDeviceSpecs(new Params(timing), timing);
    subDeviceSpecs.addSubDevice(subDeviceSpec);
    ProxyDeviceRequirement proxyDeviceRequirement = ProxyDeviceRequirement.of(subDeviceSpecs, 0);

    when(remoteDeviceConnector.connect(any())).thenReturn(leasedDeviceConnection);
    when(leasedDeviceConnection.getLocalAdbSerial()).thenReturn(LOCAL_ADB_SERIAL);
    when(leasedDeviceConnection.getRemoteDeviceId()).thenReturn(REMOTE_DEVICE_ID);
    when(leasedDeviceConnection.getHostname()).thenReturn(HOSTNAME);
    when(leasedDeviceConnection.getAllocatedDeviceProperties())
        .thenReturn(ImmutableListMultimap.of("key", "value"));

    androidRealDeviceProxy =
        new AndroidRealDeviceProxy(
            proxyDeviceRequirement, testLocator, remoteDeviceConnector, androidSystemStateUtil);
  }

  @Test
  public void leaseDevice_success() throws Exception {
    DeviceSpecification expectedDeviceSpec =
        DeviceSpecification.newBuilder()
            .setUnknownDevice(UnknownDevice.getDefaultInstance())
            .setLegacyDimensions(
                Dimensions.newBuilder()
                    .addDimensions(
                        Dimension.newBuilder().setName("devicetype").setValue("AndroidRealDevice"))
                    .addDimensions(Dimension.newBuilder().setName("decorator").setValue(DECORATOR))
                    .addDimensions(Dimension.newBuilder().setName("model").setValue(MODEL))
                    .addDimensions(
                        Dimension.newBuilder().setName("sdk_version").setValue(SDK_VERSION)))
            .build();

    Device device = androidRealDeviceProxy.leaseDevice();

    assertThat(device.getDeviceId()).isEqualTo(LOCAL_ADB_SERIAL);
    assertThat(device.getDimension("uuid")).containsExactly(REMOTE_DEVICE_ID);
    assertThat(device.getDimension("host_name")).containsExactly(HOSTNAME);
    assertThat(device.getDimension("key")).containsExactly("value");

    ArgumentCaptor<DeviceSpecification> deviceSpecCaptor =
        ArgumentCaptor.forClass(DeviceSpecification.class);
    verify(remoteDeviceConnector).connect(deviceSpecCaptor.capture());
    assertThat(deviceSpecCaptor.getValue())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(expectedDeviceSpec);
    verify(androidSystemStateUtil).waitUntilReady(LOCAL_ADB_SERIAL);
  }

  @Test
  public void releaseDevice_success() throws Exception {
    var unused = androidRealDeviceProxy.leaseDevice();
    androidRealDeviceProxy.releaseDevice();

    verify(leasedDeviceConnection).close();
  }
}
