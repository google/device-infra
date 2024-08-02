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

import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;

/** Common utilities for suite. */
public final class SuiteCommonUtil {

  private SuiteCommonUtil() {}

  /** Returns whether the given module is a module checker. */
  public static boolean isModuleChecker(Module module) {
    return module.getName().startsWith(SuiteCommon.MODULE_CHECKER_PRE)
        || module.getName().startsWith(SuiteCommon.MODULE_CHECKER_POST);
  }
}
