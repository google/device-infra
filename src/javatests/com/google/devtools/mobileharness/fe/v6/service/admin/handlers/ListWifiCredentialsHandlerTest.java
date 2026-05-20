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

package com.google.devtools.mobileharness.fe.v6.service.admin.handlers;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.ListWifiCredentialsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.ListWifiCredentialsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.WifiCredentialEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.WifiCredentialStore;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.WifiCredentialsStore;
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
public final class ListWifiCredentialsHandlerTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private WifiCredentialsStore wifiCredentialsStore;

  private ListWifiCredentialsHandler handler;

  @Before
  public void setUp() {
    handler = new ListWifiCredentialsHandler(wifiCredentialsStore);
  }

  @Test
  public void listWifiCredentials_success() throws Exception {
    WifiCredentialStore store =
        WifiCredentialStore.newBuilder()
            .addEntries(WifiCredentialEntry.newBuilder().setSsid("ssid1").setPsk("psk1"))
            .addEntries(WifiCredentialEntry.newBuilder().setSsid("ssid2").setPsk("psk2"))
            .build();
    when(wifiCredentialsStore.read()).thenReturn(immediateFuture(store));

    ListWifiCredentialsResponse response =
        handler.listWifiCredentials(ListWifiCredentialsRequest.getDefaultInstance()).get();

    assertThat(response.getWifiCredentialsList()).hasSize(2);
    assertThat(response.getWifiCredentials(0).getSsid()).isEqualTo("ssid1");
    assertThat(response.getWifiCredentials(0).getPsk()).isEqualTo("psk1");
    assertThat(response.getWifiCredentials(1).getSsid()).isEqualTo("ssid2");
    assertThat(response.getWifiCredentials(1).getPsk()).isEqualTo("psk2");
  }

  @Test
  public void listWifiCredentials_failed() throws Exception {
    when(wifiCredentialsStore.read())
        .thenReturn(
            immediateFailedFuture(
                new MobileHarnessException(
                    BasicErrorId.LOCAL_FILE_READ_BINARY_ERROR, "Read error")));

    ListenableFuture<ListWifiCredentialsResponse> future =
        handler.listWifiCredentials(ListWifiCredentialsRequest.getDefaultInstance());

    ExecutionException e = assertThrows(ExecutionException.class, future::get);
    assertThat(e).hasCauseThat().isInstanceOf(MobileHarnessException.class);
    MobileHarnessException cause = (MobileHarnessException) e.getCause();
    assertThat(cause.getErrorId()).isEqualTo(BasicErrorId.LOCAL_FILE_READ_BINARY_ERROR);
  }
}
