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

package com.google.wireless.qa.mobileharness.shared.android;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AaptTest {

  @Rule public final SetFlags flags = new SetFlags();

  @Before
  public void setUp() {
    Aapt.resetForTest();
  }

  @After
  public void tearDown() {
    Aapt.resetForTest();
  }

  @Test
  public void resetForTest_allowsRereadingFlags() throws Exception {
    Aapt aapt = new Aapt();

    flags.set("aapt", "sh");
    assertThat(aapt.getAaptPath()).isEqualTo("sh");

    flags.set("aapt", "ls");
    Aapt.resetForTest();
    assertThat(aapt.getAaptPath()).isEqualTo("ls");
  }
}
