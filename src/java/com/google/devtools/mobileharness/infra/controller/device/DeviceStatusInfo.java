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

package com.google.devtools.mobileharness.infra.controller.device;

import com.google.auto.value.AutoValue;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatusWithTimestamp;
import javax.annotation.Nullable;

/** DeviceStatusInfo contains device status, timestamp, exceptions of a device. */
@AutoValue
public abstract class DeviceStatusInfo {

  public abstract DeviceStatusWithTimestamp getDeviceStatusWithTimestamp();

  @Nullable
  public abstract ExceptionDetail getExceptionDetail();

  public static DeviceStatusInfo.Builder newBuilder() {
    return new AutoValue_DeviceStatusInfo.Builder();
  }

  /** Builder for an DeviceStatusInfo object. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract DeviceStatusInfo.Builder setDeviceStatusWithTimestamp(
        DeviceStatusWithTimestamp deviceStatusWithTimestamp);

    public abstract DeviceStatusInfo.Builder setExceptionDetail(ExceptionDetail exceptionDetail);

    public abstract DeviceStatusInfo build();
  }
}
