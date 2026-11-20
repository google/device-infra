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

package com.google.devtools.mobileharness.platform.android.shared.emulator;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import java.util.Optional;

/**
 * Configuration overrides for virtual device / CVD hardware and runtime specs.
 *
 * <p>All fields are optional; unset fields are omitted from the Cuttlefish environment config so
 * Cuttlefish uses the build's device defaults (e.g. {@code android-info.txt} and {@code
 * etc/cvd_config/cvd_config_<preset>.json}).
 */
@AutoValue
public abstract class VirtualDeviceConfig {

  /** A regular SIM, sufficient for most tests. */
  public static final int SIM_TYPE_NORMAL = 1;

  /** A SIM with carrier privileges, required by {@code CtsCarrierApiTestCases}. */
  public static final int SIM_TYPE_CTS_CARRIER_API = 2;

  public abstract Optional<Integer> cpus();

  public abstract Optional<Integer> memoryMb();

  /**
   * The SIM the modem simulator emulates, either {@link #SIM_TYPE_NORMAL} or {@link
   * #SIM_TYPE_CTS_CARRIER_API}.
   */
  public abstract Optional<Integer> modemSimulatorSimType();

  /** Whether to create a blank SD card image and expose it to the guest. */
  public abstract Optional<Boolean> useSdcard();

  public static Builder builder() {
    return new AutoValue_VirtualDeviceConfig.Builder();
  }

  public static VirtualDeviceConfig getDefaultInstance() {
    return builder().build();
  }

  /** Returns whether {@code simType} is a SIM type the modem simulator understands. */
  public static boolean isValidModemSimulatorSimType(int simType) {
    return simType == SIM_TYPE_NORMAL || simType == SIM_TYPE_CTS_CARRIER_API;
  }

  /** Builder for {@link VirtualDeviceConfig}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setCpus(int cpus);

    public abstract Builder setMemoryMb(int memoryMb);

    public abstract Builder setModemSimulatorSimType(int modemSimulatorSimType);

    public abstract Builder setUseSdcard(boolean useSdcard);

    abstract VirtualDeviceConfig autoBuild();

    public VirtualDeviceConfig build() {
      VirtualDeviceConfig config = autoBuild();
      config
          .cpus()
          .ifPresent(cpus -> Preconditions.checkArgument(cpus > 0, "cpus must be > 0: %s", cpus));
      config
          .memoryMb()
          .ifPresent(memoryMb ->
              Preconditions.checkArgument(memoryMb > 0, "memoryMb must be > 0: %s", memoryMb));
      config
          .modemSimulatorSimType()
          .ifPresent(simType ->
              Preconditions.checkArgument(
                  isValidModemSimulatorSimType(simType),
                  "modemSimulatorSimType must be %s or %s: %s",
                  SIM_TYPE_NORMAL,
                  SIM_TYPE_CTS_CARRIER_API,
                  simType));
      return config;
    }
  }
}
