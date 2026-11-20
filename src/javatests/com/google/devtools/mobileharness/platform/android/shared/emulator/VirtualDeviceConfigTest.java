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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class VirtualDeviceConfigTest {

  @Test
  public void getDefaultInstance_allFieldsEmpty() {
    VirtualDeviceConfig config = VirtualDeviceConfig.getDefaultInstance();

    assertThat(config.cpus()).isEqualTo(Optional.empty());
    assertThat(config.memoryMb()).isEqualTo(Optional.empty());
    assertThat(config.modemSimulatorSimType()).isEqualTo(Optional.empty());
    assertThat(config.useSdcard()).isEqualTo(Optional.empty());
  }

  @Test
  public void builder_customValues_success() {
    VirtualDeviceConfig config =
        VirtualDeviceConfig.builder()
            .setCpus(8)
            .setMemoryMb(16384)
            .setModemSimulatorSimType(VirtualDeviceConfig.SIM_TYPE_CTS_CARRIER_API)
            .setUseSdcard(false)
            .build();

    assertThat(config.cpus()).isEqualTo(Optional.of(8));
    assertThat(config.memoryMb()).isEqualTo(Optional.of(16384));
    assertThat(config.modemSimulatorSimType())
        .isEqualTo(Optional.of(VirtualDeviceConfig.SIM_TYPE_CTS_CARRIER_API));
    assertThat(config.useSdcard()).isEqualTo(Optional.of(false));
  }

  @Test
  public void builder_invalidCpus_throwsException() {
    VirtualDeviceConfig.Builder zeroCpusBuilder = VirtualDeviceConfig.builder().setCpus(0);
    assertThrows(IllegalArgumentException.class, zeroCpusBuilder::build);

    VirtualDeviceConfig.Builder negativeCpusBuilder = VirtualDeviceConfig.builder().setCpus(-1);
    assertThrows(IllegalArgumentException.class, negativeCpusBuilder::build);
  }

  @Test
  public void builder_invalidMemoryMb_throwsException() {
    VirtualDeviceConfig.Builder zeroMemoryBuilder = VirtualDeviceConfig.builder().setMemoryMb(0);
    assertThrows(IllegalArgumentException.class, zeroMemoryBuilder::build);

    VirtualDeviceConfig.Builder negativeMemoryBuilder =
        VirtualDeviceConfig.builder().setMemoryMb(-1024);
    assertThrows(IllegalArgumentException.class, negativeMemoryBuilder::build);
  }

  @Test
  public void builder_invalidModemSimulatorSimType_throwsException() {
    VirtualDeviceConfig.Builder zeroSimTypeBuilder =
        VirtualDeviceConfig.builder().setModemSimulatorSimType(0);
    assertThrows(IllegalArgumentException.class, zeroSimTypeBuilder::build);

    VirtualDeviceConfig.Builder unknownSimTypeBuilder =
        VirtualDeviceConfig.builder().setModemSimulatorSimType(3);
    assertThrows(IllegalArgumentException.class, unknownSimTypeBuilder::build);
  }

  @Test
  public void isValidModemSimulatorSimType_onlyAcceptsKnownSimTypes() {
    assertThat(
            VirtualDeviceConfig.isValidModemSimulatorSimType(VirtualDeviceConfig.SIM_TYPE_NORMAL))
        .isTrue();
    assertThat(
            VirtualDeviceConfig.isValidModemSimulatorSimType(
                VirtualDeviceConfig.SIM_TYPE_CTS_CARRIER_API))
        .isTrue();
    assertThat(VirtualDeviceConfig.isValidModemSimulatorSimType(0)).isFalse();
    assertThat(VirtualDeviceConfig.isValidModemSimulatorSimType(-1)).isFalse();
    assertThat(VirtualDeviceConfig.isValidModemSimulatorSimType(3)).isFalse();
  }
}
