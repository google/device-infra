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

package com.google.devtools.mobileharness.fe.v6.service.search.tjs;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsResolveChipsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSearchConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSearchRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSuggestionRequest;
import io.grpc.Status;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class NoOpTjsSearchLogicTest {

  private NoOpTjsSearchLogic logic;

  @Before
  public void setUp() {
    logic = new NoOpTjsSearchLogic();
  }

  @Test
  public void getTjsSearchConfig_returnsUnimplemented() {
    ExecutionException thrown =
        assertThrows(
            ExecutionException.class,
            () -> logic.getTjsSearchConfig(TjsSearchConfigRequest.getDefaultInstance()).get());
    assertThat(thrown).hasCauseThat().isInstanceOf(FeServiceException.class);
    FeServiceException feEx = (FeServiceException) thrown.getCause();
    assertThat(feEx.getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
  }

  @Test
  public void searchTjs_returnsUnimplemented() {
    ExecutionException thrown =
        assertThrows(
            ExecutionException.class,
            () -> logic.searchTjs(TjsSearchRequest.getDefaultInstance()).get());
    assertThat(thrown).hasCauseThat().isInstanceOf(FeServiceException.class);
    FeServiceException feEx = (FeServiceException) thrown.getCause();
    assertThat(feEx.getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
  }

  @Test
  public void getTjsSuggestions_returnsUnimplemented() {
    ExecutionException thrown =
        assertThrows(
            ExecutionException.class,
            () -> logic.getTjsSuggestions(TjsSuggestionRequest.getDefaultInstance()).get());
    assertThat(thrown).hasCauseThat().isInstanceOf(FeServiceException.class);
    FeServiceException feEx = (FeServiceException) thrown.getCause();
    assertThat(feEx.getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
  }

  @Test
  public void resolveTjsChips_returnsUnimplemented() {
    ExecutionException thrown =
        assertThrows(
            ExecutionException.class,
            () -> logic.resolveTjsChips(TjsResolveChipsRequest.getDefaultInstance()).get());
    assertThat(thrown).hasCauseThat().isInstanceOf(FeServiceException.class);
    FeServiceException feEx = (FeServiceException) thrown.getCause();
    assertThat(feEx.getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
  }
}
