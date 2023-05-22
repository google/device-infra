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

package com.google.devtools.deviceaction.common.schemas;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.ActionOptions.Options;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ActionOptionsTest {

  @Test
  public void build_expectedResult() throws Exception {
    Options options =
        Options.builder().addTrueBoolOptions("option1").addFalseBoolOptions("option2").build();

    assertThat(options.trueBoolOptions()).containsExactly("option1");
    assertThat(options.falseBoolOptions()).containsExactly("option2");
  }

  @Test
  public void build_duplicatedBoolOptions_throwsException() throws Exception {
    assertThrows(
        DeviceActionException.class,
        () -> Options.builder().addTrueBoolOptions("option").addFalseBoolOptions("option").build());
  }

  @Test
  public void getOnlyValue_expectedResult() throws Exception {
    Options options =
        Options.builder()
            .addKeyValues("key1", "value")
            .addKeyValues("key2", "value1", "value2")
            .build();

    assertThat(options.getOnlyValue("key1")).hasValue("value");
    assertThat(options.getOnlyValue("key3")).isEmpty();
    assertThrows(DeviceActionException.class, () -> options.getOnlyValue("key2"));
  }
}
