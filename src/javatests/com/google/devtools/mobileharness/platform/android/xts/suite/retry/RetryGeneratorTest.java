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

package com.google.devtools.mobileharness.platform.android.xts.suite.retry;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Test;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.nio.file.Path;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class RetryGeneratorTest {

  private static final Module MODULE_1_V8A =
      Module.newBuilder()
          .setAbi("arm64-v8a")
          .setName("Module1")
          .addTestCase(
              TestCase.newBuilder()
                  .setName("TestClass1")
                  .addTest(Test.newBuilder().setName("Test1").setResult("pass"))
                  .addTest(Test.newBuilder().setName("Test2").setResult("fail")))
          .addTestCase(
              TestCase.newBuilder()
                  .setName("TestClass2")
                  .addTest(Test.newBuilder().setName("Test1").setResult("incomplete"))
                  .addTest(Test.newBuilder().setName("Test2").setResult("skipped")))
          .build();

  private static final Module MODULE_1_V7A =
      Module.newBuilder()
          .setAbi("armeabi-v7a")
          .setName("Module1")
          .addTestCase(
              TestCase.newBuilder()
                  .setName("TestClass1")
                  .addTest(Test.newBuilder().setName("Test1").setResult("fail"))
                  .addTest(Test.newBuilder().setName("Test2").setResult("fail")))
          .addTestCase(
              TestCase.newBuilder()
                  .setName("TestClass2")
                  .addTest(Test.newBuilder().setName("Test1").setResult("skipped"))
                  .addTest(Test.newBuilder().setName("Test2").setResult("incomplete")))
          .build();

  private static final Module MODULE_2_V8A =
      Module.newBuilder()
          .setAbi("arm64-v8a")
          .setName("Module2")
          .addTestCase(
              TestCase.newBuilder()
                  .setName("TestClass1")
                  .addTest(Test.newBuilder().setName("Test1").setResult("pass"))
                  .addTest(Test.newBuilder().setName("Test2").setResult("pass")))
          .addTestCase(
              TestCase.newBuilder()
                  .setName("TestClass2")
                  .addTest(Test.newBuilder().setName("Test1").setResult("pass"))
                  .addTest(Test.newBuilder().setName("Test2").setResult("pass")))
          .build();

  private static final Module NON_TF_MODULE_3_V8A =
      Module.newBuilder()
          .setAbi("arm64-v8a")
          .setName("Module3")
          .setIsNonTfModule(true)
          .addTestCase(
              TestCase.newBuilder()
                  .setName("TestClass1")
                  .addTest(Test.newBuilder().setName("Test1").setResult("pass"))
                  .addTest(Test.newBuilder().setName("Test2").setResult("fail")))
          .build();

  private static final Result REPORT_1 =
      Result.newBuilder()
          .addModuleInfo(MODULE_1_V8A)
          .addModuleInfo(MODULE_1_V7A)
          .addModuleInfo(MODULE_2_V8A)
          .addModuleInfo(NON_TF_MODULE_3_V8A)
          .build();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private PreviousResultLoader previousResultLoader;

  @Inject private RetryGenerator retryGenerator;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @org.junit.Test
  public void generateRetrySubPlan_defaultRetryType() throws Exception {
    Path resultsDir = Path.of("/path/to/results_dir");
    int previousSessionId = 0;
    when(previousResultLoader.loadPreviousResult(resultsDir, previousSessionId))
        .thenReturn(REPORT_1);

    SubPlan subPlan =
        retryGenerator.generateRetrySubPlan(
            RetryArgs.builder()
                .setResultsDir(resultsDir)
                .setPreviousSessionId(previousSessionId)
                .build());

    SetMultimap<String, String> subPlanIncludeFiltersMultimap = subPlan.getIncludeFiltersMultimap();

    assertThat(Multimaps.asMap(subPlanIncludeFiltersMultimap))
        .containsExactly(
            "arm64-v8a Module1",
            ImmutableSet.of("TestClass1#Test2", "TestClass2#Test1", "TestClass2#Test2"),
            "armeabi-v7a Module1",
            ImmutableSet.of("ALL"));

    SetMultimap<String, String> subPlanNonTfIncludeFiltersMultimap =
        subPlan.getNonTfIncludeFiltersMultimap();

    assertThat(Multimaps.asMap(subPlanNonTfIncludeFiltersMultimap))
        .containsExactly("arm64-v8a Module3", ImmutableSet.of("TestClass1#Test2"));
  }

  @org.junit.Test
  public void generateRetrySubPlan_retryTypeIsNotExecuted() throws Exception {
    Path resultsDir = Path.of("/path/to/results_dir");
    int previousSessionId = 0;
    when(previousResultLoader.loadPreviousResult(resultsDir, previousSessionId))
        .thenReturn(REPORT_1);

    SubPlan subPlan =
        retryGenerator.generateRetrySubPlan(
            RetryArgs.builder()
                .setResultsDir(resultsDir)
                .setPreviousSessionId(previousSessionId)
                .setRetryType(RetryType.NOT_EXECUTED)
                .build());

    SetMultimap<String, String> subPlanIncludeFiltersMultimap = subPlan.getIncludeFiltersMultimap();

    assertThat(Multimaps.asMap(subPlanIncludeFiltersMultimap))
        .containsExactly(
            "arm64-v8a Module1",
            ImmutableSet.of("TestClass2#Test1", "TestClass2#Test2"),
            "armeabi-v7a Module1",
            ImmutableSet.of("TestClass2#Test1", "TestClass2#Test2"));

    SetMultimap<String, String> subPlanNonTfIncludeFiltersMultimap =
        subPlan.getNonTfIncludeFiltersMultimap();

    assertThat(Multimaps.asMap(subPlanNonTfIncludeFiltersMultimap)).isEmpty();
  }
}
