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

package com.google.devtools.mobileharness.infra.ats.console.result.report;

import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.LoggedFile;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Metric;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.RunHistory;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.StackTrace;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Test;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestFailure;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.xml.stream.events.StartElement;

/** A POJO used to track compatibility report parsing state. */
final class Context {

  final Deque<StartElement> tagStack;

  final Result.Builder resultBuilder;

  RunHistory.Builder currentRunHistory;

  Module.Builder currentModule;

  TestCase.Builder currentTestCase;

  Test.Builder currentTest;

  TestFailure.Builder currentTestFailure;

  StackTrace.Builder currentStackTrace;

  LoggedFile.Builder currentBugReport;

  LoggedFile.Builder currentLogcat;

  LoggedFile.Builder currentScreenshot;

  Metric.Builder currentMetric;

  public Context(Result.Builder resultBuilder) {
    this.tagStack = new ArrayDeque<>();
    this.resultBuilder = resultBuilder;
  }
}
