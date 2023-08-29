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
import com.google.devtools.mobileharness.shared.constant.closeable.NonThrowingAutoCloseable;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.ForwardTestMessageRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.ForwardTestMessageResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestDetailRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestDetailResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestGenDataRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestGenDataResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestStatusRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestStatusResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.KickOffTestRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.KickOffTestResponse;
import javax.annotation.Nullable;

/** RPC stub interface for talking to Lab Server for ExecTestService. */
public interface ExecTestStub extends NonThrowingAutoCloseable {

  /**
   * Kicks off a test on the allocated device. You should make sure test info and all files have
   * already been sent.
   */
  @CanIgnoreReturnValue
  KickOffTestResponse kickOffTest(KickOffTestRequest request) throws RpcExceptionWithErrorId;

  /** Kicks off a test on the allocated device with the impersonation user. */
  @CanIgnoreReturnValue
  default KickOffTestResponse kickOffTest(
      KickOffTestRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return kickOffTest(request);
  }

  /**
   * Gets the current status of a test.
   *
   * <p>It should only be invoked by MH client RemoteTestRunner because MH test messaging system
   * depends on this assumption. See go/mh-messaging-design.
   */
  GetTestStatusResponse getTestStatus(GetTestStatusRequest request) throws RpcExceptionWithErrorId;

  /** Gets the current status of a test with the impersonation user. */
  default GetTestStatusResponse getTestStatus(
      GetTestStatusRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return getTestStatus(request);
  }

  /** Gets the test detail information of a running test. */
  GetTestDetailResponse getTestDetail(GetTestDetailRequest request) throws RpcExceptionWithErrorId;

  /** Gets the test detail information of a running test with the impersonation user. */
  default GetTestDetailResponse getTestDetail(
      GetTestDetailRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return getTestDetail(request);
  }

  /** Forwards a test message to the lab side. */
  ForwardTestMessageResponse forwardTestMessage(ForwardTestMessageRequest request)
      throws RpcExceptionWithErrorId;

  /** Forwards a test message to the lab side with the impersonation user. */
  @CanIgnoreReturnValue
  default ForwardTestMessageResponse forwardTestMessage(
      ForwardTestMessageRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return forwardTestMessage(request);
  }

  /** Gets generated files and properties of a test. */
  GetTestGenDataResponse getTestGenData(GetTestGenDataRequest request)
      throws RpcExceptionWithErrorId;

  /** Gets generated files and properties of a test with the impersonation user. */
  default GetTestGenDataResponse getTestGenData(
      GetTestGenDataRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return getTestGenData(request);
  }
}
