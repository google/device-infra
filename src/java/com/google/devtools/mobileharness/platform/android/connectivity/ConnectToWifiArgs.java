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

package com.google.devtools.mobileharness.platform.android.connectivity;

import com.google.auto.value.AutoValue;
import java.time.Duration;
import java.util.Optional;

/**
 * Wrapper for arguments used for connecting device to a given wifi used by {@link
 * AndroidConnectivityUtil}.
 */
@AutoValue
public abstract class ConnectToWifiArgs {

  /** The serial number of the device. */
  public abstract String serial();

  /** The SDK version (i.e., API level) of the device. */
  public abstract int sdkVersion();

  /** The SSID of the Wifi. */
  public abstract String wifiSsid();

  /** The password of the Wifi. */
  public abstract Optional<String> wifiPsk();

  /** Whether to scan for hidden SSID. */
  public abstract Optional<Boolean> scanSsid();

  /** Waiting timeout to connect device to given ssid. */
  public abstract Optional<Duration> waitTimeout();

  /**
   * Connects the device to given SSID forcely. It may timeout eventually if the given SSID is not
   * discoverd by the device before command timeout.
   */
  public abstract Optional<Boolean> forceTryConnect();

  public static Builder builder() {
    return new AutoValue_ConnectToWifiArgs.Builder();
  }

  public abstract Builder toBuilder();

  /** Auto value builder for {@link ConnectToWifiArgs}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setSerial(String serial);

    public abstract Builder setSdkVersion(int sdkVersion);

    public abstract Builder setWifiSsid(String wifiSsid);

    public abstract Builder setWifiPsk(String wifiPsk);

    public abstract Builder setScanSsid(boolean scanSsid);

    public abstract Builder setWaitTimeout(Duration waitTimeout);

    public abstract Builder setForceTryConnect(boolean forceTryConnect);

    public abstract ConnectToWifiArgs build();
  }
}
