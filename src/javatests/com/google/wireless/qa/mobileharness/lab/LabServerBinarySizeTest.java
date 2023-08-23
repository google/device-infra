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

package com.google.wireless.qa.mobileharness.lab;

import com.google.devtools.deviceinfra.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.mobileharness.shared.util.file.local.BinarySizeChecker;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LabServerBinarySizeTest {

  private static final long MAX_LAB_SERVER_BINARY_SIZE_BYTE = 23_000_000L;

  private static final String LAB_SERVER_BINARY_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "java/com/google/wireless/qa/mobileharness/lab/lab_server_oss_deploy.jar");

  @Test
  public void checkLabServerBinarySize() throws Exception {
    BinarySizeChecker.checkBinarySize(
        "OmniLab lab server", MAX_LAB_SERVER_BINARY_SIZE_BYTE, LAB_SERVER_BINARY_FILE_PATH);
  }
}
