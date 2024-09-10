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

package com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.rpc.stub;

import com.google.auto.value.AutoValue;
import com.google.devtools.common.metrics.stability.rpc.RpcExceptionWithErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;

/** A wrapper class for all exception information. */
@AutoValue
public abstract class RpcExceptionWrapper {

  /** The error id of the exception. */
  abstract InfraErrorId errorId();

  /** The error message. */
  abstract String message();

  public MobileHarnessException mobileHarnessException(RpcExceptionWithErrorId e) {
    return new MobileHarnessException(errorId(), message(), e);
  }

  public static RpcExceptionWrapper create(InfraErrorId errorId, String message) {
    return new AutoValue_RpcExceptionWrapper(errorId, message);
  }
}
