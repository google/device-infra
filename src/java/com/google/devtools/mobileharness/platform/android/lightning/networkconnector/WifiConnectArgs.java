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

package com.google.devtools.mobileharness.platform.android.lightning.networkconnector;

import com.google.auto.value.AutoValue;
import java.time.Duration;
import java.util.Optional;

/** Wrapper for arguments used for connecting device to a given wifi. */
@AutoValue
public abstract class WifiConnectArgs {

  /** The SSID of the Wifi. */
  public abstract String wifiSsid();

  /** The password of the Wifi. */
  public abstract Optional<String> wifiPsk();

  /** Whether to scan for hidden SSID. */
  public abstract Optional<Boolean> scanSsid();

  /**
   * Whether to log caught MobileHarnessException only when fail to connect wifi, instead of
   * throwing it out.
   */
  public abstract Optional<Boolean> logFailuresOnly();

  /** Waiting timeout to connect device to given ssid. */
  public abstract Optional<Duration> waitTimeout();

  /** Number of retrying if failed to connect the device to the given ssid. */
  public abstract Optional<Integer> retryNum();

  public static Builder builder() {
    return new AutoValue_WifiConnectArgs.Builder();
  }

  /** Auto value builder for {@link WifiConnectArgs}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setWifiSsid(String wifiSsid);

    public abstract Builder setWifiPsk(String wifiPsk);

    public abstract Builder setScanSsid(boolean scanSsid);

    public abstract Builder setLogFailuresOnly(boolean logFailuresOnly);

    public abstract Builder setWaitTimeout(Duration waitTimeout);

    public abstract Builder setRetryNum(Integer retryNum);

    public abstract WifiConnectArgs build();
  }
}
