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

package com.google.devtools.mobileharness.infra.client.longrunningservice;

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.devtools.deviceinfra.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OlcServerBinarySizeTest {

  private static final long MAX_BASE_SERVER_BINARY_SIZE_BYTE = 21_000_000L;

  private static final long MAX_ANDROID_SERVER_BINARY_SIZE_BYTE = 26_000_000L;

  private static final String BASE_SERVER_BINARY_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness"
              + "/infra/client/longrunningservice/OlcServerForTesting_deploy.jar");

  private static final String ANDROID_SERVER_BINARY_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness"
              + "/infra/client/longrunningservice/OlcServerWithAndroidDevice_deploy.jar");

  @Test
  public void checkBaseServerBinarySize() throws Exception {
    checkBinarySize(
        "OmniLab client with no_op test",
        MAX_BASE_SERVER_BINARY_SIZE_BYTE,
        BASE_SERVER_BINARY_FILE_PATH);
  }

  @Test
  public void checkAndroidServerBinarySize() throws Exception {
    checkBinarySize(
        "OmniLab client with Android device",
        MAX_ANDROID_SERVER_BINARY_SIZE_BYTE,
        ANDROID_SERVER_BINARY_FILE_PATH);
  }

  private static void checkBinarySize(String name, long maxSizeByte, String filePath)
      throws Exception {
    assertWithMessage(
            "The binary size of %s should be less than %s bytes. If"
                + " you are sure that the new added deps are necessary, please"
                + " update the number and explain the necessity. file_path=%s",
            name, maxSizeByte, filePath)
        .that(new LocalFileUtil().getFileSize(filePath))
        .isLessThan(maxSizeByte);
  }
}
