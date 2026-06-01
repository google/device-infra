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
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigSection;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigUiStatus;

/** Helper interface for Config Pusher logic. */
public interface ConfigPusherHelper {
  /** Decorates the UI status based on Config Pusher settings in the lab config. */
  HostConfigUiStatus decorateUiStatus(HostConfigUiStatus baseStatus, LabConfig labConfig);

  /** Validates the update request against Config Pusher restrictions. */
  void validateUpdate(HostConfigSection section, HostConfig incoming, LabConfig existing)
      throws MobileHarnessException;

  /** Checks if a section is restricted by Config Pusher. */
  boolean isSectionRestricted(HostConfigSection section, LabConfig existing);
}
