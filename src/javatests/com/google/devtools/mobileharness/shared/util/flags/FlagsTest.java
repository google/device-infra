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

package com.google.devtools.mobileharness.shared.util.flags;

import static org.junit.Assert.assertThrows;

import com.google.common.truth.Truth;
import com.google.devtools.mobileharness.shared.util.flags.core.FlagSpec;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FlagsTest {

  @Rule public final SetFlags flags = new SetFlags();

  @Test
  public void flags_areOrderedByName() {
    Field[] fields = Flags.class.getDeclaredFields();

    List<String> flagNames = new ArrayList<>();
    for (Field field : fields) {
      var flagSpec = getFlagSpecOss(field);
      if (flagSpec != null) {
        flagNames.add(flagSpec.name());
      }
    }

    Truth.assertWithMessage(
            "Flags in Flags.java should be sorted by @FlagSpec.name alphabetically.")
        .that(flagNames)
        .isInOrder();
  }

  @Test
  public void checkConstraints_unknownModemSimulatorSimType_throwsException() {
    flags.set("android_jit_emulator_modem_simulator_sim_type", "3");

    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, Flags::checkConstraints);

    Truth.assertThat(e)
        .hasMessageThat()
        .contains("--android_jit_emulator_modem_simulator_sim_type");
  }

  @Test
  public void checkConstraints_knownModemSimulatorSimTypes_pass() {
    flags.set("android_jit_emulator_modem_simulator_sim_type", "1");
    Flags.checkConstraints();

    flags.set("android_jit_emulator_modem_simulator_sim_type", "2");
    Flags.checkConstraints();
  }

  @Test
  public void checkConstraints_unsetModemSimulatorSimType_passes() {
    Flags.checkConstraints();
  }

  private static FlagSpec getFlagSpecOss(Field field) {
    return field.getAnnotation(FlagSpec.class);
  }
}
