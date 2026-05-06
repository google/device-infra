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

import com.google.common.truth.Truth;
import com.google.devtools.mobileharness.shared.util.flags.core.FlagSpec;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FlagsTest {

  @Ignore("b/506875938")
  @Test
  public void flags_areOrderedByName() {
    Field[] fields = Flags.class.getDeclaredFields();

    List<String> flagNames = new ArrayList<>();
    for (Field field : fields) {
      FlagSpec flagSpec = field.getAnnotation(FlagSpec.class);
      if (flagSpec != null) {
        flagNames.add(flagSpec.name());
      }
    }

    Truth.assertWithMessage(
            "Flags in Flags.java should be sorted by @FlagSpec.name alphabetically.")
        .that(flagNames)
        .isInOrder();
  }
}
