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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteTestFilter;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlanHelper;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

/** To generate the subplan for retrying a previous session. */
public class RetryGenerator {

  private final PreviousResultLoader previousResultLoader;

  @Inject
  RetryGenerator(PreviousResultLoader previousResultLoader) {
    this.previousResultLoader = previousResultLoader;
  }

  /** Generates a {@link SubPlan} that contains the retry modules and tests. */
  public SubPlan generateRetrySubPlan(RetryArgs retryArgs) throws MobileHarnessException {
    Path resultsDir = retryArgs.resultsDir();
    Result previousResult;
    if (retryArgs.previousSessionId().isEmpty()) {
      previousResult =
          previousResultLoader.loadPreviousResult(
              resultsDir, retryArgs.previousSessionIndex().orElseThrow());
    } else {
      previousResult =
          previousResultLoader.loadPreviousResult(resultsDir, retryArgs.previousSessionId().get());
    }

    Optional<RetryType> retryType = retryArgs.retryType();
    ImmutableSet<RetryType> retryTypes =
        retryType
            .map(ImmutableSet::of)
            .orElseGet(() -> ImmutableSet.of(RetryType.FAILED, RetryType.NOT_EXECUTED));

    List<String> includeFiltersFromPrevResult = previousResult.getIncludeFilterList();
    List<String> excludeFiltersFromPrevResult = previousResult.getExcludeFilterList();

    ImmutableSet<String> retryTypesStr =
        retryTypes.stream().map(rt -> Ascii.toLowerCase(rt.name())).collect(toImmutableSet());

    SubPlan subPlan =
        SubPlanHelper.createSubPlanForPreviousResult(
            previousResult,
            retryTypesStr,
            /* addSubPlanCmd= */ false,
            includeFiltersFromPrevResult.stream()
                .map(SuiteTestFilter::create)
                .collect(toImmutableSet()),
            excludeFiltersFromPrevResult.stream()
                .map(SuiteTestFilter::create)
                .collect(toImmutableSet()),
            retryArgs.passedInModules());
    SubPlanHelper.addPassedInFiltersToSubPlan(
        subPlan,
        retryArgs.passedInIncludeFilters(),
        retryArgs.passedInExcludeFilters(),
        retryArgs.allNonTfModules());
    return subPlan;
  }
}
