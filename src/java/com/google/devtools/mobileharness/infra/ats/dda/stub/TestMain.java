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

import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.infra.ats.dda.stub.AtsLabInfoStub.DeviceGroupMetadata;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import java.util.List;
import java.util.Map;

/** */
final class TestMain {

  public static void main(String[] args) throws Exception {
    // String host = "100.103.191.19";
    // int port = 7030;
    // String host = "localhost";
    // int port = 9000;

    String host = "34.85.87.237"; // test demo
    int port = 7030;

    ManagedChannel channel = NettyChannelBuilder.forAddress(host, port).usePlaintext().build();

    testGetDeviceGroups(channel);
    testCreateAndCancelSession(channel, "sm-g991b", 31);
  }

  private static void testGetDeviceGroups(ManagedChannel channel) throws Exception {
    AtsLabInfoStub labInfoStub = new AtsLabInfoStub(channel);
    Map<DeviceGroupMetadata, List<DeviceInfo>> groups = labInfoStub.queryDeviceGroups();
    System.out.println("======Querying catalog=====");
    for (DeviceGroupMetadata group : groups.keySet()) {
      System.out.println(
          String.format("======Catalog: %s, size: %d", group, groups.get(group).size()));
    }
  }

  private static void testCreateAndCancelSession(Channel channel, String model, int apiLevel)
      throws Exception {
    AtsDdaStub ddaStub = new AtsDdaStub(channel);
    System.out.println("======Creating session with model: " + model + " apiLevel: " + apiLevel);
    String sessionId = ddaStub.createSession("test-session", model, apiLevel);
    System.out.println("======Created session Id:" + sessionId);

    System.out.println("======sleeping...");
    Thread.sleep(10000);

    System.out.println("====== Session Result:" + ddaStub.getSession(sessionId));

    System.out.println("======sleeping...");
    Thread.sleep(10000);

    System.out.println("====== Session Result:" + ddaStub.getSession(sessionId));

    System.out.println("====== Cancel session Id:" + sessionId);

    System.out.println(ddaStub.cancelSession(sessionId));
  }
}
