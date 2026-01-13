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

package com.google.devtools.mobileharness.infra.ats.common.constant;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuiltinFlagsTest {

  @Test
  public void atsLabServerFlags_notAtsLabServer_returnsEmpty() {
    assertThat(BuiltinFlags.atsLabServerFlags(ImmutableMap.of())).isEmpty();
  }

  @Test
  public void atsLabServerFlags_invalidAtsLabServerType_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            BuiltinFlags.atsLabServerFlags(
                ImmutableMap.of(
                    BuiltinFlags.ATS_LAB_SERVER_TYPE_PROPERTY_KEY, "wrong-ats-lab-server-type")));
  }

  @Test
  public void checkValidAtsLabServerTypes() {
    assertThat(BuiltinFlags.ATS_LAB_SERVER_FLAGS.keySet())
        .containsExactly(
            "on-prem",
            "omni-dda",
            "omni-public-testing",
            "omni-internal-testing",
            "omni-xts-testing",
            "omni-private");
  }
}
