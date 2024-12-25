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

package com.google.devtools.mobileharness.infra.client.api.util.resourcefederation;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.infra.client.api.proto.ResourceFederationProto.ResourceFederation;
import com.google.devtools.mobileharness.infra.client.api.proto.ResourceFederationProto.ServerResource;
import com.google.devtools.mobileharness.infra.client.api.proto.ResourceFederationProto.ServerResourceType;
import com.google.devtools.mobileharness.infra.client.api.proto.ServerLocatorProto.GrpcServerLocator;
import com.google.devtools.mobileharness.infra.client.api.proto.ServerLocatorProto.ServerLocator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ResourceFederationUtilTest {

  @Test
  public void findServerLocator_success() {
    ServerLocator serverLocator =
        ServerLocator.newBuilder()
            .setGrpcServerLocator(
                GrpcServerLocator.newBuilder().setHostname("localhost").setPort(1234))
            .build();
    ResourceFederation resourceFederation =
        ResourceFederation.newBuilder()
            .addServerResources(
                ServerResource.newBuilder()
                    .setServerResourceType(ServerResourceType.MASTER)
                    .setServerLocator(serverLocator))
            .build();
    assertThat(
            ResourceFederationUtil.findServerLocator(resourceFederation, ServerResourceType.MASTER))
        .hasValue(serverLocator);
    assertThat(
            ResourceFederationUtil.findServerLocator(
                resourceFederation, ServerResourceType.GRPC_RELAY))
        .isEmpty();
  }
}
