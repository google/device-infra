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

package com.google.devtools.mobileharness.fe.v6.service.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.proto.common.Universe;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.GroupMembershipProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseFactory;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class ConfigServiceLogicImplTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private DeviceDataLoader deviceDataLoader;
  @Bind @Mock private ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  @Bind private final ListeningExecutorService executor = newDirectExecutorService();
  @Bind @Mock private UniverseFactory universeFactory;
  @Bind @Mock private GroupMembershipProvider groupMembershipProvider;
  @Bind @Mock private ConfigurationProvider configurationProvider;

  private ConfigServiceLogicImpl configServiceLogicImpl;

  @Before
  public void setUp() {
    when(universeFactory.create(anyString())).thenReturn(Universe.getDefaultInstance());
    configServiceLogicImpl =
        Guice.createInjector(BoundFieldModule.of(this)).getInstance(ConfigServiceLogicImpl.class);
  }

  @Test
  public void getDeviceConfig_invalidUniverse_fails() throws Exception {
    GetDeviceConfigRequest request =
        GetDeviceConfigRequest.newBuilder().setId("device").setUniverse("invalid").build();

    when(universeFactory.create("invalid")).thenThrow(new IllegalArgumentException("invalid"));

    ExecutionException e =
        assertThrows(
            ExecutionException.class, () -> configServiceLogicImpl.getDeviceConfig(request).get());
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
  }
}
