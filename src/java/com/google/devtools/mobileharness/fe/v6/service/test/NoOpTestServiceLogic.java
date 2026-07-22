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

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.test.GetTestLogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.test.GetTestLogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.test.GetTestRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.test.GetTestResponse;

/** No-op implementation of {@link TestServiceLogic} used when no MOSS backend is available. */
public final class NoOpTestServiceLogic implements TestServiceLogic {

  @Override
  public ListenableFuture<GetTestResponse> getTest(GetTestRequest request) {
    return immediateFailedFuture(
        new UnsupportedOperationException("TestService.GetTest is not available."));
  }

  @Override
  public ListenableFuture<GetTestLogResponse> getTestLog(GetTestLogRequest request) {
    return immediateFailedFuture(
        new UnsupportedOperationException("TestService.GetTestLog is not available."));
  }
}
