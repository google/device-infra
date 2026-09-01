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

package com.google.devtools.mobileharness.fe.v6.service.shared.auth;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;

/** Checks the caller's Cloud IAM access to MobileHarness device and host resources. */
public interface IamPermissionChecker {

  /** Checks mobileharness.device.config permission (owner only) for a device. */
  ListenableFuture<Boolean> canConfigDevice(String deviceId, UniverseScope universe);

  /** Checks mobileharness.labdefaultdevice.config permission (owner only) for a host. */
  ListenableFuture<Boolean> canConfigHost(String hostName, UniverseScope universe);

  /** Checks mobileharness.labdefaultdevice.exec permission (owner + executor) for a host. */
  ListenableFuture<Boolean> canExecHost(String hostName, UniverseScope universe);
}
