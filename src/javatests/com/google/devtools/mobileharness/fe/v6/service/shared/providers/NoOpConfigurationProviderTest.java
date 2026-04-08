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

package com.google.devtools.mobileharness.fe.v6.service.shared.providers;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NoOpConfigurationProviderTest {

  private NoOpConfigurationProvider provider;

  @Before
  public void setUp() {
    provider = new NoOpConfigurationProvider();
  }

  @Test
  public void getDeviceConfig_returnEmpty() throws Exception {
    assertThat(provider.getDeviceConfig("device_id", new UniverseScope.SelfUniverse()).get())
        .isEmpty();
  }

  @Test
  public void getLabConfig_returnEmpty() throws Exception {
    assertThat(provider.getLabConfig("host_name", new UniverseScope.SelfUniverse()).get())
        .isEmpty();
  }

  @Test
  public void updateDeviceConfig_throwUnsupported() {
    ListenableFuture<Void> future =
        provider.updateDeviceConfig(
            "device_id", DeviceConfig.getDefaultInstance(), new UniverseScope.SelfUniverse());
    ExecutionException thrown = assertThrows(ExecutionException.class, future::get);
    assertThat(thrown).hasCauseThat().isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void updateLabConfig_throwUnsupported() {
    ListenableFuture<Void> future =
        provider.updateLabConfig(
            "host_name", LabConfig.getDefaultInstance(), new UniverseScope.SelfUniverse());
    ExecutionException thrown = assertThrows(ExecutionException.class, future::get);
    assertThat(thrown).hasCauseThat().isInstanceOf(UnsupportedOperationException.class);
  }
}
