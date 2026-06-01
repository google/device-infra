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

package com.google.devtools.mobileharness.fe.v6.service.config.util;

import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigSection;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigUiStatus;

/** No-op implementation of {@link ConfigPusherHelper}. */
public final class NoOpConfigPusherHelper implements ConfigPusherHelper {

  @Override
  public HostConfigUiStatus decorateUiStatus(HostConfigUiStatus baseStatus, LabConfig labConfig) {
    return baseStatus;
  }

  @Override
  public void validateUpdate(HostConfigSection section, HostConfig incoming, LabConfig existing) {
    // No-op
  }

  @Override
  public boolean isSectionRestricted(HostConfigSection section, LabConfig existing) {
    return false;
  }

  @Override
  public boolean unlockHostProperties(LabConfig.Builder builder) {
    return false;
  }
}
