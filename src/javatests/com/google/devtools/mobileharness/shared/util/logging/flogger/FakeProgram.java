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

package com.google.devtools.mobileharness.shared.util.logging.flogger;

import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.IMPORTANT;

import com.google.common.flogger.FluentLogger;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessLogger;

public class FakeProgram {

  static {
    FloggerFormatter.initialize();
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static void main(String[] args) {
    MobileHarnessLogger.init();

    logger.atInfo().with(IMPORTANCE, IMPORTANT).log("Foo");
  }

  private FakeProgram() {}
}
