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

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;

class LocalFileStorageBackend implements StorageBackend {

  private final LocalFileUtil localFileUtil;

  LocalFileStorageBackend(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
  }

  @Override
  public boolean exists(String key) {
    return localFileUtil.isFileExist(key);
  }

  @Override
  public String read(String key) throws MobileHarnessException {
    return localFileUtil.readFile(key);
  }

  @Override
  public void write(String key, String content) throws MobileHarnessException {
    localFileUtil.writeToFile(key, content);
  }

  @Override
  public void remove(String key) throws MobileHarnessException, InterruptedException {
    localFileUtil.removeFileOrDir(key);
  }
}
