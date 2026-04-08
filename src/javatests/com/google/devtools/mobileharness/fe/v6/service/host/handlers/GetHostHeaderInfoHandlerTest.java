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
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostHeaderInfoRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostHeaderInfo;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class GetHostHeaderInfoHandlerTest {

  private GetHostHeaderInfoHandler handler;

  @Before
  public void setUp() {
    handler = new GetHostHeaderInfoHandler();
  }

  @Test
  public void getHostHeaderInfo_routedUniverse_returnsEmptyActions() throws Exception {
    GetHostHeaderInfoRequest request =
        GetHostHeaderInfoRequest.newBuilder().setHostName("my_host").build();
    UniverseScope universe = new UniverseScope.RoutedUniverse("");

    HostHeaderInfo info = handler.getHostHeaderInfo(request, universe).get();

    assertThat(info.getHostName()).isEqualTo("my_host");
    assertThat(info.getActions()).isEqualToDefaultInstance();
  }

  @Test
  public void getHostHeaderInfo_selfUniverse_returnsEmptyActions() throws Exception {
    GetHostHeaderInfoRequest request =
        GetHostHeaderInfoRequest.newBuilder().setHostName("my_host").build();
    UniverseScope universe = new UniverseScope.SelfUniverse();

    HostHeaderInfo info = handler.getHostHeaderInfo(request, universe).get();

    assertThat(info.getHostName()).isEqualTo("my_host");
    assertThat(info.getActions()).isEqualToDefaultInstance();
  }
}
