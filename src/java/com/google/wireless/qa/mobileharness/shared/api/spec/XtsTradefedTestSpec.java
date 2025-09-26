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

package com.google.wireless.qa.mobileharness.shared.api.spec;

import com.google.wireless.qa.mobileharness.shared.api.annotation.FileAnnotation;

/** The parameters/files for XtsTradefedTest driver. */
@SuppressWarnings("InterfaceWithOnlyStatics")
public interface XtsTradefedTestSpec {

  @FileAnnotation(required = false, help = "The test record proto files for the previous session.")
  public static final String TAG_PREV_SESSION_TEST_RECORD_PB_FILES =
      "prev_session_test_record_pb_files";
}
