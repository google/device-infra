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

package com.google.devtools.mobileharness.infra.lab;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.shared.util.file.local.BinarySizeChecker;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LabServerBinarySizeTest {

  private static final ImmutableSet<String> LAB_SERVER_LARGE_RESOURCE_PATH_ALLOWLIST =
      ImmutableSet.of();

  private static final long MAX_LAB_SERVER_BINARY_SIZE_BYTE = 27_250_000L;
  private static final long MAX_LAB_SERVER_BINARY_RESOURCE_FILE_SIZE_BYTE = 800_000L;

  private static final String LAB_SERVER_BINARY_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "java/com/google/devtools/mobileharness/infra/lab/lab_server_oss_deploy.jar");
  private static final String LAB_SERVER_BINARY_SOURCE_PATH =
      "google3/third_party/deviceinfra/src/java/com/google/devtools/mobileharness/infra"
          + "/lab:lab_server_oss_deploy.jar";

  @Test
  public void checkLabServerBinarySize() throws Exception {
    BinarySizeChecker.checkBinarySize(
        "lab_server_oss_deploy.jar",
        MAX_LAB_SERVER_BINARY_SIZE_BYTE,
        LAB_SERVER_BINARY_FILE_PATH,
        LAB_SERVER_BINARY_SOURCE_PATH);
  }

  @Test
  public void checkLabServerBinaryLargeResources() throws Exception {
    BinarySizeChecker.checkBinaryLargeResourceFiles(
        "lab_server_oss_deploy.jar",
        LAB_SERVER_BINARY_FILE_PATH,
        MAX_LAB_SERVER_BINARY_RESOURCE_FILE_SIZE_BYTE,
        LAB_SERVER_LARGE_RESOURCE_PATH_ALLOWLIST,
        LAB_SERVER_BINARY_SOURCE_PATH,
        "google3/third_party/deviceinfra/src/javatests/com/google/devtools/mobileharness/infra"
            + "/lab/LabServerBinarySizeTest.java#LAB_SERVER_LARGE_RESOURCE_PATH_ALLOWLIST");
  }
}
