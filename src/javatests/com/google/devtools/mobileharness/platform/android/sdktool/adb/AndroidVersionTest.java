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
public final class AndroidVersionTest {

  @Test
  public void getStartSdkVersion_return26ForOreo() {
    assertThat(AndroidVersion.OREO.getStartSdkVersion()).isEqualTo(26);
  }

  @Test
  public void getEndSdkVersion_return27ForOreo() {
    assertThat(AndroidVersion.OREO.getEndSdkVersion()).isEqualTo(27);
  }

  @Test
  public void toCodeNameInitial_getExpected() {
    assertThat(AndroidVersion.toCodeNameInitial(1)).isEmpty();
    assertThat(AndroidVersion.toCodeNameInitial(31)).hasValue("S");
    assertThat(AndroidVersion.toCodeNameInitial(32)).hasValue("S");
    assertThat(AndroidVersion.toCodeNameInitial(33)).hasValue("T");
    assertThat(AndroidVersion.toCodeNameInitial(100)).isEmpty();
  }
}
