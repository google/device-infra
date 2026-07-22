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

package com.google.devtools.mobileharness.fe.v6.service.test;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.test.GetTestLogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.test.GetTestLogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.test.GetTestRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.test.GetTestResponse;

/** Logic interface for the Test Detail service. */
public interface TestServiceLogic {

  /** Gets the full test detail (overview, execution details, troubleshooting, sub-tests). */
  ListenableFuture<GetTestResponse> getTest(GetTestRequest request);

  /** Gets a paginated chunk of the test log. */
  ListenableFuture<GetTestLogResponse> getTestLog(GetTestLogRequest request);
}
