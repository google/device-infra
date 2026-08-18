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

package com.google.devtools.mobileharness.infra.client.api.mode.local;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.infra.client.api.controller.allocation.reserver.DeviceReserver;
import com.google.devtools.mobileharness.infra.client.longrunningservice.util.SessionDeviceCache;
import com.google.devtools.mobileharness.shared.labinfo.LabInfoService;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LocalModeRuleTest {

  @Rule public final LocalModeRule rule = new LocalModeRule();

  @Test
  public void getLocalMode_returnsInstance() {
    assertThat(rule.getLocalMode()).isNotNull();
  }

  @Test
  public void getModule_bindsRequiredDependencies() {
    Injector injector = Guice.createInjector(rule.getModule());

    LocalModeEnvironment env = injector.getInstance(LocalModeEnvironment.class);
    assertThat(env).isNotNull();
    assertThat(injector.getInstance(DeviceReserver.class)).isNotNull();
    assertThat(injector.getInstance(LabInfoService.class))
        .isSameInstanceAs(env.getLabInfoService());
    assertThat(injector.getInstance(SessionDeviceCache.class)).isNotNull();
  }

  @Test
  public void localModeModule_bindsRequiredDependencies() {
    Injector injector = Guice.createInjector(new LocalModeModule());

    LocalModeEnvironment env = injector.getInstance(LocalModeEnvironment.class);
    assertThat(env).isSameInstanceAs(LocalModeEnvironment.getInstance());
    assertThat(injector.getInstance(DeviceReserver.class)).isNotNull();
    assertThat(injector.getInstance(LabInfoService.class))
        .isSameInstanceAs(env.getLabInfoService());
    assertThat(injector.getInstance(SessionDeviceCache.class)).isNotNull();
  }
}
