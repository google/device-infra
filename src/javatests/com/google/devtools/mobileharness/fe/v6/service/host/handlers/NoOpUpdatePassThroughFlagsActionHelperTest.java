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

package com.google.devtools.mobileharness.fe.v6.service.host.handlers;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.UpdatePassThroughFlagsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.UpdatePassThroughFlagsResponse;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class NoOpUpdatePassThroughFlagsActionHelperTest {

  private NoOpUpdatePassThroughFlagsActionHelper helper;

  @Before
  public void setUp() {
    helper = new NoOpUpdatePassThroughFlagsActionHelper();
  }

  @Test
  public void updatePassThroughFlags_returnsError() throws Exception {
    UpdatePassThroughFlagsRequest request = UpdatePassThroughFlagsRequest.getDefaultInstance();

    ListenableFuture<UpdatePassThroughFlagsResponse> resultFuture =
        helper.updatePassThroughFlags(request, new UniverseScope.SelfUniverse());

    UpdatePassThroughFlagsResponse response = Futures.getDone(resultFuture);
    assertThat(response.getSuccess()).isFalse();
    assertThat(response.getError().getMessage()).contains("Only supported internally");
  }
}
