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

package com.google.devtools.mobileharness.fe.v6.service.admin;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.immediateVoidFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.fe.v6.service.admin.handlers.ListWifiCredentialsHandler;
import com.google.devtools.mobileharness.fe.v6.service.admin.handlers.SetWifiCredentialsHandler;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.ListWifiCredentialsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.ListWifiCredentialsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.SetWifiCredentialsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.SetWifiCredentialsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.WifiCredentialEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.WifiCredentialStore;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.WifiCredentialsStore;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AdminServiceLogicImplTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private WifiCredentialsStore wifiCredentialsStore;

  private AdminServiceLogicImpl logic;

  @Before
  public void setUp() {
    SetWifiCredentialsHandler setHandler = new SetWifiCredentialsHandler(wifiCredentialsStore);
    ListWifiCredentialsHandler listHandler = new ListWifiCredentialsHandler(wifiCredentialsStore);
    logic = new AdminServiceLogicImpl(setHandler, listHandler);
  }

  @Test
  public void listWifiCredentials_success() throws Exception {
    WifiCredentialStore store =
        WifiCredentialStore.newBuilder()
            .addEntries(WifiCredentialEntry.newBuilder().setSsid("ssid1").setPsk("psk1"))
            .build();
    when(wifiCredentialsStore.read()).thenReturn(immediateFuture(store));

    ListWifiCredentialsResponse response =
        logic.listWifiCredentials(ListWifiCredentialsRequest.getDefaultInstance()).get();

    assertThat(response.getWifiCredentialsList()).hasSize(1);
    assertThat(response.getWifiCredentials(0).getSsid()).isEqualTo("ssid1");
    assertThat(response.getWifiCredentials(0).getPsk()).isEqualTo("psk1");
  }

  @Test
  public void setWifiCredentials_success() throws Exception {
    SetWifiCredentialsRequest request =
        SetWifiCredentialsRequest.newBuilder()
            .addWifiCredentials(WifiCredentialEntry.newBuilder().setSsid("ssid1").setPsk("psk1"))
            .build();
    when(wifiCredentialsStore.write(any(WifiCredentialStore.class)))
        .thenReturn(immediateVoidFuture());

    SetWifiCredentialsResponse response = logic.setWifiCredentials(request).get();

    assertThat(response.getCredentialsCount()).isEqualTo(1);
  }
}
