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

package com.google.devtools.mobileharness.platform.android.lightning.bundletool;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.nio.file.Path;

/** Utility for Bundletool operations. */
public final class BundletoolUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private BundletoolUtil() {}

  /** Generates JavaSystemProperties by extracting the temporary directory from TestInfo. */
  public static JavaSystemProperties createJavaSystemProperties(TestInfo testInfo) {
    JavaSystemProperties.Builder javaPropsBuilder = JavaSystemProperties.builder();
    try {
      String tmpDir = testInfo.getTmpFileDir();
      if (tmpDir != null && !tmpDir.isEmpty()) {
        javaPropsBuilder = javaPropsBuilder.setJavaTmpDir(Path.of(tmpDir));
      }
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Failed to get temp dir from testInfo. Error: %s", e.getMessage());
    }
    return javaPropsBuilder.build();
  }
}
