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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.WifiCredentialStore;

/**
 * Interface for reading and writing Wi-Fi credentials.
 *
 * <p>Internal implementation uses encrypted storage. OSS implementation is a no-op.
 *
 * @see NoOpWifiCredentialsStore
 */
public interface WifiCredentialsStore {

  /**
   * Reads the current Wi-Fi credentials asynchronously.
   *
   * @return a future containing the stored credentials, or an empty store if none exist
   */
  ListenableFuture<WifiCredentialStore> read();

  /**
   * Writes Wi-Fi credentials asynchronously, replacing any existing data.
   *
   * @param store the credentials to write
   * @return a future that completes when the write is done
   */
  ListenableFuture<Void> write(WifiCredentialStore store);
}
