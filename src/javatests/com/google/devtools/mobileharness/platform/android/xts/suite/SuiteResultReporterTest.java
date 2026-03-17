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

package com.google.devtools.mobileharness.platform.android.xts.suite;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryStatsHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SuiteResultReporterTest {

  @Test
  public void getSummary_ignoresSkippedModules() {
    RetryStatsHelper mockRetryStatsHelper = mock(RetryStatsHelper.class);
    SuiteResultReporter reporter = new SuiteResultReporter(mockRetryStatsHelper);

    Result report =
        Result.newBuilder()
            .addModuleInfo(
                Module.newBuilder().setName("module1").setAbi("arm64").setDone(true).build())
            .addModuleInfo(
                Module.newBuilder().setName("module2").setAbi("arm64").setDone(false).build())
            .addModuleInfo(
                Module.newBuilder()
                    .setName("module3")
                    .setAbi("arm64")
                    .setDone(true)
                    .setSkipped(true)
                    .build())
            .build();

    String summary = reporter.getSummary(report, /* previousResult= */ null);

    // Module 1 is complete, Module 2 is incomplete, Module 3 is skipped.
    // Total modules should be 2, complete modules 1.
    assertThat(summary).contains("1/2 modules completed");
  }
}
