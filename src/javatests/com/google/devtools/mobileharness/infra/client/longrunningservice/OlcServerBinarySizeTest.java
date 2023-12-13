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

import com.google.devtools.mobileharness.shared.util.file.local.BinarySizeChecker;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OlcServerBinarySizeTest {

  private static final long MAX_BASE_SERVER_BINARY_SIZE_BYTE = 23_600_000L;
  private static final long MAX_ANDROID_SERVER_BINARY_SIZE_BYTE = 26_000_000L;
  private static final long MAX_ATS_SERVER_BINARY_SIZE_BYTE = 27_000_000L;

  private static final String BASE_SERVER_BINARY_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness"
              + "/infra/client/longrunningservice/OlcServerForTesting_deploy.jar");
  private static final String ANDROID_SERVER_BINARY_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness"
              + "/infra/client/longrunningservice/OlcServerWithAndroidDevice_deploy.jar");
  private static final String ATS_SERVER_BINARY_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "java/com/google/devtools/mobileharness/infra/ats/common/olcserver/ats_olc_server_deploy.jar");

  @Test
  public void checkBaseServerBinarySize() throws Exception {
    BinarySizeChecker.checkBinarySize(
        "OmniLab client with no_op test",
        MAX_BASE_SERVER_BINARY_SIZE_BYTE,
        BASE_SERVER_BINARY_FILE_PATH);
  }

  @Test
  public void checkAndroidServerBinarySize() throws Exception {
    BinarySizeChecker.checkBinarySize(
        "OmniLab client with Android device",
        MAX_ANDROID_SERVER_BINARY_SIZE_BYTE,
        ANDROID_SERVER_BINARY_FILE_PATH);
  }

  @Test
  public void checkAtsServerBinarySize() throws Exception {
    BinarySizeChecker.checkBinarySize(
        "ATS OmniLab client", MAX_ATS_SERVER_BINARY_SIZE_BYTE, ATS_SERVER_BINARY_FILE_PATH);
  }
}
