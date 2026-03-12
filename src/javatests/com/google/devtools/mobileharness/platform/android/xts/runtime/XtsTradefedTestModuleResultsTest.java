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

package com.google.devtools.mobileharness.platform.android.xts.runtime;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedTestModuleResults.ModuleInfo;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class XtsTradefedTestModuleResultsTest {

  @Test
  public void encodeAndDecodeToString() {
    XtsTradefedTestModuleResults results =
        new XtsTradefedTestModuleResults(
            ImmutableMap.of(
                "inv_1",
                ImmutableList.of(
                    new ModuleInfo("module_1", /* isRunning= */ false),
                    new ModuleInfo("module_2", /* isRunning= */ true)),
                "inv_2",
                ImmutableList.of(
                    new ModuleInfo(
                        "module_3",
                        /* isRunning= */ true,
                        /* testsExpected= */ 100,
                        /* testsCompleted= */ 50,
                        /* testsFailed= */ 1,
                        /* testsPassed= */ 48,
                        /* testsSkipped= */ 1,
                        Runtime.version().feature() < 17
                            ? Duration.ofSeconds(0)
                            : Duration.ofSeconds(1234)))));

    String string = results.encodeToString();

    assertThat(XtsTradefedTestModuleResults.decodeFromString(string)).isEqualTo(results);
  }
}
