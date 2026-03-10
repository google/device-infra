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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedTestModuleResults.ModuleInfo;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class XtsTradefedTestModuleResultsTest {

  @Test
  public void encodeAndDecodeToString() {
    ImmutableMap<String, List<ModuleInfo>> runningModulesMap =
        ImmutableMap.of(
            "invocation-1",
            ImmutableList.of(
                new ModuleInfo(
                    /* id= */ "id1",
                    /* isRunning= */ true,
                    /* testsExpected= */ 10,
                    /* testsCompleted= */ 5,
                    /* testsFailed= */ 1,
                    /* testsPassed= */ 4,
                    /* testsSkipped= */ 0,
                    Duration.ZERO),
                new ModuleInfo(
                    /* id= */ "id2",
                    /* isRunning= */ false,
                    /* testsExpected= */ 5,
                    /* testsCompleted= */ 5,
                    /* testsFailed= */ 0,
                    /* testsPassed= */ 5,
                    /* testsSkipped= */ 0,
                    Duration.ZERO)),
            "invocation-2",
            ImmutableList.of(
                new ModuleInfo(
                    /* id= */ "id3",
                    /* isRunning= */ true,
                    /* testsExpected= */ 2,
                    /* testsCompleted= */ 0,
                    /* testsFailed= */ 0,
                    /* testsPassed= */ 0,
                    /* testsSkipped= */ 0,
                    Duration.ZERO)));

    XtsTradefedTestModuleResults moduleResults =
        new XtsTradefedTestModuleResults(runningModulesMap);

    String encoded = moduleResults.encodeToString();
    XtsTradefedTestModuleResults decoded = XtsTradefedTestModuleResults.decodeFromString(encoded);

    assertThat(decoded).isEqualTo(moduleResults);
  }

  @Test
  public void decodeFromOldFormat() {
    String invocation1 = Base64.getEncoder().encodeToString("invocation-1".getBytes(UTF_8));

    String moduleId = Base64.getEncoder().encodeToString("id1".getBytes(UTF_8));
    String moduleIsRunning = "true";
    String moduleStr =
        Base64.getEncoder().encodeToString((moduleId + ";" + moduleIsRunning).getBytes(UTF_8));

    String encoded = invocation1 + ":" + moduleStr;

    XtsTradefedTestModuleResults decoded = XtsTradefedTestModuleResults.decodeFromString(encoded);

    XtsTradefedTestModuleResults expected =
        new XtsTradefedTestModuleResults(
            ImmutableMap.of(
                "invocation-1",
                ImmutableList.of(
                    new ModuleInfo(
                        /* id= */ "id1",
                        /* isRunning= */ true,
                        /* testsExpected= */ 0,
                        /* testsCompleted= */ 0,
                        /* testsFailed= */ 0,
                        /* testsPassed= */ 0,
                        /* testsSkipped= */ 0,
                        Duration.ZERO))));

    assertThat(decoded).isEqualTo(expected);
  }
}
