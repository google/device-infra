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

package com.google.devtools.mobileharness.fe.v6.service.search.allsite;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ResolveAllSiteQueryRequest;
import io.grpc.Status;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class NoOpAllSiteQueryResolverTest {

  @Test
  public void resolveAllSiteQuery_failsWithUnimplemented() {
    NoOpAllSiteQueryResolver resolver = new NoOpAllSiteQueryResolver();

    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () ->
                resolver
                    .resolveAllSiteQuery(
                        ResolveAllSiteQueryRequest.newBuilder().setQuery("device-1").build())
                    .get());

    assertThat(e).hasCauseThat().isInstanceOf(FeServiceException.class);
    assertThat(((FeServiceException) e.getCause()).getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
    assertThat(e).hasCauseThat().hasMessageThat().contains("not supported in standalone ATS");
  }
}
