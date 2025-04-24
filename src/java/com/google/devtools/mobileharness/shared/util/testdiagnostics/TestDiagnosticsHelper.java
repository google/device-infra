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

package com.google.devtools.mobileharness.shared.util.testdiagnostics;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;

/** Utility class for handling TestDiagnostics. */
public class TestDiagnosticsHelper {

  /**
   * Gets the path of the {@link TestDiagnostics} directory. Test runners can write TestDiagnostics
   * files to this directory on the lab side and it will be merged with the final TestDiagnostics on
   * the client side before uploading to Sponge.
   *
   * @param testInfo the TestInfo for the test
   */
  public String getTestDiagnosticsDir(TestInfo testInfo) throws MobileHarnessException {
    return PathUtil.join(testInfo.getGenFileDir(), "testdiagnostics");
  }
}
