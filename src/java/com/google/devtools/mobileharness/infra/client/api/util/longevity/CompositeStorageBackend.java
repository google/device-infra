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

package com.google.devtools.mobileharness.infra.client.api.util.longevity;

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.util.Optional;

class CompositeStorageBackend implements StorageBackend {

  private final LocalFileStorageBackend localFileStorageBackend;

  CompositeStorageBackend() {
    this(new LocalFileStorageBackend(new LocalFileUtil()));
  }

  @VisibleForTesting
  CompositeStorageBackend(LocalFileStorageBackend localFileStorageBackend) {
    this.localFileStorageBackend = localFileStorageBackend;
  }

  @Override
  public boolean exists(String key) {
    return findBackend(key).map(backend -> backend.exists(key)).orElse(false);
  }

  @Override
  public String read(String key) throws MobileHarnessException {
    return findBackend(key).orElseThrow(UnsupportedOperationException::new).read(key);
  }

  @Override
  public void write(String key, String content) throws MobileHarnessException {
    findBackend(key)
        .orElseThrow(
            () ->
                new MobileHarnessException(
                    InfraErrorId.CLIENT_LONGEVITY_STORAGE_BACKEND_NOT_FOUND,
                    String.format(
                        "Failed to save content because longevity storage backend for key [%s] is"
                            + " not found, content=[%s]",
                        key, content)))
        .write(key, content);
  }

  @Override
  public void remove(String key) throws MobileHarnessException, InterruptedException {
    findBackend(key).orElseThrow(UnsupportedOperationException::new).remove(key);
  }

  private Optional<StorageBackend> findBackend(String key) {
    return Optional.of(localFileStorageBackend);
  }
}
