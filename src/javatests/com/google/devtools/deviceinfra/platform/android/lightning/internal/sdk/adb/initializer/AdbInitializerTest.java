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

package com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.initializer;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AdbInitializerTest {

  @Rule public final SetFlags flags = new SetFlags();

  @Before
  public void setUp() {
    AdbInitializer.resetForTest();
  }

  @After
  public void tearDown() {
    AdbInitializer.resetForTest();
  }

  @Test
  public void resetForTest_allowsRereadingFlags() throws Exception {
    // 1. Set first custom adb path flag and instantiate Adb
    flags.set("adb", "/dummy/path/1");
    Adb adb = new Adb();
    String path1 = adb.getAdbPath();
    assertThat(path1).isEqualTo("/dummy/path/1");

    // 2. Reset AdbInitializer and set a different custom adb path flag
    AdbInitializer.resetForTest();
    flags.set("adb", "/dummy/path/2");

    // 3. Retrieve adb path again and verify it successfully got updated to the new path
    String path2 = adb.getAdbPath();
    assertThat(path2).isEqualTo("/dummy/path/2");
  }
}
