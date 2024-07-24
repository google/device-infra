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

package com.google.devtools.mobileharness.shared.util.comm.stub;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.grpc.stub.AbstractBlockingStub;
import java.time.Duration;

/** Utility for gRPC stubs. */
public class Stubs {

  /**
   * Note that this method should be called for each rpc call because the deadline instant is
   * calculated when this method is called.
   */
  public static <S extends AbstractBlockingStub<S>> S withDeadline(S stub, Duration deadline) {
    return stub.withDeadlineAfter(deadline.toMillis(), MILLISECONDS);
  }

  private Stubs() {}
}
