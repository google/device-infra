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

package com.google.devtools.mobileharness.platform.android.sdktool.adb;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AndroidAdbOutputUtilTest {

  @Test
  public void toBooleanValue_true() {
    assertThat(AndroidAdbOutputUtil.convertPropertyValueToBoolean("true")).hasValue(true);
    assertThat(AndroidAdbOutputUtil.convertPropertyValueToBoolean("1")).hasValue(true);
    assertThat(AndroidAdbOutputUtil.convertPropertyValueToBoolean("0")).hasValue(false);
    assertThat(AndroidAdbOutputUtil.convertPropertyValueToBoolean("false")).hasValue(false);
    assertThat(AndroidAdbOutputUtil.convertPropertyValueToBoolean("abc")).isEmpty();
    assertThat(AndroidAdbOutputUtil.convertPropertyValueToBoolean("")).isEmpty();
  }
}
