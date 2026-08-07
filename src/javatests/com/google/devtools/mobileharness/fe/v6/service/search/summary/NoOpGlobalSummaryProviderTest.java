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

package com.google.devtools.mobileharness.fe.v6.service.search.summary;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.GetGlobalSummaryRequest;
import io.grpc.Status;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link NoOpGlobalSummaryProvider}. */
@RunWith(JUnit4.class)
public final class NoOpGlobalSummaryProviderTest {

  private final NoOpGlobalSummaryProvider provider = new NoOpGlobalSummaryProvider();

  @Test
  public void getGlobalSummary_throwsUnimplemented() {
    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () -> provider.getGlobalSummary(GetGlobalSummaryRequest.getDefaultInstance()).get());

    assertThat(e).hasCauseThat().isInstanceOf(FeServiceException.class);
    FeServiceException cause = (FeServiceException) e.getCause();
    assertThat(cause.getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
  }
}
