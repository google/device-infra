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

package com.google.devtools.mobileharness.infra.ats.dda.stub;

import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;

/** Connection to the ATS OLC server and provides stubs to access OLC services. */
public class AtsOlcServerConnection {

  private final AtsDdaStub ddaStub;
  private final AtsLabInfoStub labInfoStub;

  public AtsOlcServerConnection(String olcServerAddress, int olcServerPort) {
    ManagedChannel channel =
        NettyChannelBuilder.forAddress(olcServerAddress, olcServerPort).usePlaintext().build();
    this.ddaStub = new AtsDdaStub(channel);
    this.labInfoStub = new AtsLabInfoStub(channel);
  }

  public AtsDdaStub getDdaStub() {
    return ddaStub;
  }

  public AtsLabInfoStub getLabInfoStub() {
    return labInfoStub;
  }
}
