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

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.shared.util.file.local.BinarySizeChecker;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OlcServerBinarySizeTest {

  private static final ImmutableSet<String> BASE_OLC_SERVER_LARGE_RESOURCE_PATH_ALLOWLIST =
      ImmutableSet.of();
  private static final ImmutableSet<String> ATS_OLC_SERVER_LARGE_RESOURCE_PATH_ALLOWLIST =
      ImmutableSet.of();

  private static final long MAX_BASE_OLC_SERVER_BINARY_SIZE_BYTE = 24_600_000L;
  private static final long MAX_ATS_OLC_SERVER_BINARY_SIZE_BYTE = 27_500_000L;
  private static final long MAX_OLC_SERVER_BINARY_RESOURCE_FILE_SIZE_BYTE = 1_000_000L;

  private static final String BASE_OLC_SERVER_BINARY_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness"
              + "/infra/client/longrunningservice/OlcServerForTesting_deploy.jar");
  private static final String ATS_OLC_SERVER_BINARY_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "java/com/google/devtools/mobileharness/infra/ats/common/olcserver/ats_olc_server_deploy.jar");

  @Test
  public void checkBaseServerBinarySize() throws Exception {
    BinarySizeChecker.checkBinarySize(
        "OlcServerForTesting_deploy.jar",
        MAX_BASE_OLC_SERVER_BINARY_SIZE_BYTE,
        BASE_OLC_SERVER_BINARY_FILE_PATH);
  }

  @Test
  public void checkBaseServerBinaryLargeResources() throws Exception {
    BinarySizeChecker.checkBinaryLargeResourceFiles(
        "OlcServerForTesting_deploy.jar",
        BASE_OLC_SERVER_BINARY_FILE_PATH,
        MAX_OLC_SERVER_BINARY_RESOURCE_FILE_SIZE_BYTE,
        BASE_OLC_SERVER_LARGE_RESOURCE_PATH_ALLOWLIST,
        "google3/third_party/deviceinfra/src/javatests/com/google/devtools/mobileharness"
            + "/infra/client/longrunningservice:OlcServerForTesting_deploy.jar",
        "google3/third_party/deviceinfra/src/javatests/com/google/devtools/mobileharness"
            + "/infra/client/longrunningservice/OlcServerBinarySizeTest.java"
            + "#BASE_OLC_SERVER_LARGE_RESOURCE_PATH_ALLOWLIST");
  }

  @Test
  public void checkAtsServerBinarySize() throws Exception {
    BinarySizeChecker.checkBinarySize(
        "ats_olc_server_deploy.jar",
        MAX_ATS_OLC_SERVER_BINARY_SIZE_BYTE,
        ATS_OLC_SERVER_BINARY_FILE_PATH);
  }

  @Test
  public void checkAtsServerBinaryLargeResources() throws Exception {
    BinarySizeChecker.checkBinaryLargeResourceFiles(
        "ats_olc_server_deploy.jar",
        ATS_OLC_SERVER_BINARY_FILE_PATH,
        MAX_OLC_SERVER_BINARY_RESOURCE_FILE_SIZE_BYTE,
        ATS_OLC_SERVER_LARGE_RESOURCE_PATH_ALLOWLIST,
        "google3/third_party/deviceinfra/src/java/com/google/devtools/mobileharness"
            + "/infra/ats/common/olcserver:ats_olc_server_deploy.jar",
        "google3/third_party/deviceinfra/src/javatests/com/google/devtools/mobileharness"
            + "/infra/client/longrunningservice/OlcServerBinarySizeTest.java"
            + "#ATS_OLC_SERVER_LARGE_RESOURCE_PATH_ALLOWLIST");
  }
}
