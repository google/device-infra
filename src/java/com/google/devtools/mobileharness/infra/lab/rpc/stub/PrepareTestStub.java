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

package com.google.devtools.mobileharness.infra.lab.rpc.stub;

import com.google.devtools.common.metrics.stability.rpc.RpcExceptionWithErrorId;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CloseTestRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CloseTestResponse;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CreateTestRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CreateTestResponse;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.GetTestEngineStatusRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.GetTestEngineStatusResponse;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.StartTestEngineRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.StartTestEngineResponse;
import com.google.devtools.mobileharness.shared.constant.closeable.NonThrowingAutoCloseable;
import javax.annotation.Nullable;

/** Stub interface of {@code PrepareTestService}. */
public interface PrepareTestStub extends NonThrowingAutoCloseable {

  CreateTestResponse createTest(CreateTestRequest request) throws RpcExceptionWithErrorId;

  /** Creates test with the impersonation user. */
  default CreateTestResponse createTest(
      CreateTestRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return createTest(request);
  }

  GetTestEngineStatusResponse getTestEngineStatus(GetTestEngineStatusRequest request)
      throws RpcExceptionWithErrorId;

  /** Gets the test engine status with the impersonation user. */
  default GetTestEngineStatusResponse getTestEngineStatus(
      GetTestEngineStatusRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return getTestEngineStatus(request);
  }

  StartTestEngineResponse startTestEngine(StartTestEngineRequest request)
      throws RpcExceptionWithErrorId;

  /** Starts the test engine with the impersonation user. */
  default StartTestEngineResponse startTestEngine(
      StartTestEngineRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return startTestEngine(request);
  }

  CloseTestResponse closeTest(CloseTestRequest request) throws RpcExceptionWithErrorId;

  /** Closes the test with the impersonation user. */
  default CloseTestResponse closeTest(CloseTestRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return closeTest(request);
  }
}
