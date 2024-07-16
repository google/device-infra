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

package com.google.devtools.mobileharness.shared.util.base;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StackSetTest {

  private final StackSet<String> stackSet = new StackSet<>();

  @Test
  public void getLast() {
    assertThat(stackSet.getLast()).isNull();

    assertThrows(NullPointerException.class, () -> stackSet.add(null));

    stackSet.add("e1");
    assertThat(stackSet.getLast()).isEqualTo("e1");

    assertThrows(IllegalStateException.class, () -> stackSet.add("e1"));

    stackSet.add("e2");
    assertThat(stackSet.getLast()).isEqualTo("e2");

    stackSet.add("e3");
    assertThat(stackSet.getLast()).isEqualTo("e3");

    stackSet.add("e4");
    assertThat(stackSet.getLast()).isEqualTo("e4");

    stackSet.removeUntilLast("e5");
    assertThat(stackSet.getLast()).isEqualTo("e4");

    stackSet.removeUntilLast("e4");
    assertThat(stackSet.getLast()).isEqualTo("e3");

    stackSet.removeUntilLast("e2");
    assertThat(stackSet.getLast()).isEqualTo("e1");

    stackSet.removeUntilLast("e1");
    assertThat(stackSet.getLast()).isNull();
  }
}
