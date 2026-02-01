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

package com.google.devtools.mobileharness.infra.ats.console.result.report.moblytestentryconverter;

import com.google.common.base.Splitter;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyTestEntry;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.StackTrace;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Test;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestFailure;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.MoblyResult;
import com.google.devtools.mobileharness.platform.android.xts.common.TestStatus;

/** Default implementation of {@link MoblyTestEntryConverter}. */
public class DefaultMoblyTestEntryConverter implements MoblyTestEntryConverter {

  public DefaultMoblyTestEntryConverter() {}

  @Override
  public Test convert(MoblyTestEntry testEntry) {
    MoblyResult result = testEntry.getResult();
    Test.Builder testBuilder =
        Test.newBuilder().setName(testEntry.getTestName()).setResult(getTestStatus(result));
    if (result == MoblyResult.SKIP) {
      testBuilder.setSkipped(true);
    }
    if (testEntry.getStacktrace().isPresent()) {
      String stackTrace = testEntry.getStacktrace().get();
      testBuilder.setFailure(
          TestFailure.newBuilder()
              .setMsg(
                  testEntry
                      .getDetails()
                      // Use the first line of stack trace as error message if no details
                      .orElse(Splitter.on('\n').splitToList(stackTrace).get(0)))
              .setStackTrace(StackTrace.newBuilder().setContent(stackTrace)));
    }
    return testBuilder.build();
  }

  private static String getTestStatus(MoblyResult moblyResult) {
    return TestStatus.convertMoblyResultToTestStatusCompatibilityString(moblyResult);
  }
}
