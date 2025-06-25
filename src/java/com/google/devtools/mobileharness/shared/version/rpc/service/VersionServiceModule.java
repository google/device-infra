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

package com.google.devtools.mobileharness.shared.version.rpc.service;

import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.proto.VersionProto;
import com.google.inject.AbstractModule;

/** Configures the VersionServiceImpl class. */
public final class VersionServiceModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(VersionServiceImpl.class)
        .toInstance(
            new VersionServiceImpl(
                VersionProto.Version.newBuilder()
                    .setVersion(Version.LAB_VERSION.toString())
                    .setType("LAB_VERSION")
                    .build()));
  }
}
