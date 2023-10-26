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

package com.google.devtools.mobileharness.shared.util.file.local;

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;

/** Checker for checking binary sizes. */
public class BinarySizeChecker {

  public static void checkBinarySize(String name, long maxSizeByte, String filePath)
      throws MobileHarnessException {
    assertWithMessage(
            "The binary size of %s should be less than %s bytes. If you are sure that the new added"
                + " deps are necessary, please update the number and explain the necessity (what"
                + " libs are added to the binary, their sizes, why they are necessary) in the"
                + " change description. file_path=%s",
            name, maxSizeByte, filePath)
        .that(new LocalFileUtil().getFileSize(filePath))
        .isLessThan(maxSizeByte);
  }

  private BinarySizeChecker() {}
}
