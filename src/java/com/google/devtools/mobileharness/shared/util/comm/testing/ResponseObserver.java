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

package com.google.devtools.mobileharness.shared.util.comm.testing;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.devtools.mobileharness.shared.util.comm.testing.proto.DummyServiceProto.DummyResponse;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Collects all the responses for an async call. */
public final class ResponseObserver implements StreamObserver<DummyResponse> {

  private final List<DummyResponse> responseCollector = new ArrayList<>();
  private final CountDownLatch latch;
  private final List<Throwable> errors = new ArrayList<>();

  /**
   * Creates a new response observer.
   *
   * @param expectedCount the number of responses to wait for.
   */
  public ResponseObserver(int expectedCount) {
    latch = new CountDownLatch(expectedCount);
  }

  public ResponseObserver() {
    this(1);
  }

  @Override
  public void onNext(DummyResponse value) {
    responseCollector.add(value);
    latch.countDown();
  }

  @Override
  public void onError(Throwable t) {
    errors.add(t);
    while (latch.getCount() > 0) {
      latch.countDown();
    }
  }

  @Override
  public void onCompleted() {}

  /** Blockingly waits for all the responses to be received and returns them. */
  public ImmutableList<DummyResponse> waitAndGetResponses() throws InterruptedException {
    latch.await(1, TimeUnit.MINUTES);
    return ImmutableList.copyOf(responseCollector);
  }

  /** Blockingly waits for a single response to be received and returns it. */
  public DummyResponse waitAndGetOnlyResponse() throws InterruptedException {
    return Iterables.getOnlyElement(waitAndGetResponses());
  }

  /** Blockingly waits for the first error to be received and returns it. */
  public Optional<Throwable> waitAndGetFirstError() throws InterruptedException {
    latch.await(1, TimeUnit.MINUTES);
    return errors.stream().findFirst();
  }

  long countResponseToWait() {
    return latch.getCount();
  }
}
