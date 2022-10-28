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

package com.google.wireless.qa.mobileharness.shared.controller.event;

import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;

/** Signals that a new local device becomes error. */
public class LocalDeviceErrorEvent extends LocalDeviceEvent {

  private final ExceptionDetail deviceError;

  public LocalDeviceErrorEvent(
      String deviceControlId, String deviceUuid, String deviceType, ExceptionDetail deviceError) {
    super(deviceControlId, deviceUuid, deviceType);
    this.deviceError = deviceError;
  }

  public ExceptionDetail getDeviceError() {
    return deviceError;
  }
}
