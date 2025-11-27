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

package com.google.devtools.mobileharness.service.deviceconfig;

import com.google.devtools.mobileharness.service.deviceconfig.storage.LocalFileStorageClient;
import com.google.devtools.mobileharness.service.deviceconfig.storage.StorageClient;
import com.google.inject.AbstractModule;

/** The Guice module for device config service. */
public class DeviceConfigModule extends AbstractModule {

  @Override
  protected void configure() {
    // TODO: b/460296020 - Add a flag to control which storage client to use.
    bind(StorageClient.class).to(LocalFileStorageClient.class);
  }
}
