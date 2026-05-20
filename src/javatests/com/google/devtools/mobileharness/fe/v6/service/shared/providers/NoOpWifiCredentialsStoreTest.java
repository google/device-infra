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
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.WifiCredentialStore;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class NoOpWifiCredentialsStoreTest {
  private NoOpWifiCredentialsStore store;

  @Before
  public void setUp() {
    store = new NoOpWifiCredentialsStore();
  }

  @Test
  public void read_returnsEmpty() throws Exception {
    WifiCredentialStore result = store.read().get();
    assertThat(result).isEqualTo(WifiCredentialStore.getDefaultInstance());
  }

  @Test
  public void write_throwsUnsupportedOperation() throws Exception {
    ListenableFuture<Void> future = store.write(WifiCredentialStore.getDefaultInstance());

    ExecutionException e = assertThrows(ExecutionException.class, future::get);
    assertThat(e).hasCauseThat().isInstanceOf(UnsupportedOperationException.class);
    assertThat(e.getCause())
        .hasMessageThat()
        .contains("Writing Wi-Fi credentials is not supported in the current environment");
  }
}
