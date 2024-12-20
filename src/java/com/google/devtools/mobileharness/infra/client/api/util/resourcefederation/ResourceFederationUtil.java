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

import static com.google.common.collect.Maps.toImmutableEnumMap;
import static com.google.devtools.mobileharness.infra.client.api.util.serverlocator.ServerLocatorUtil.getMasterServerLocator;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.client.api.proto.ResourceFederationProto.ResourceFederation;
import com.google.devtools.mobileharness.infra.client.api.proto.ResourceFederationProto.ServerResource;
import com.google.devtools.mobileharness.infra.client.api.proto.ResourceFederationProto.ServerResourceType;
import com.google.devtools.mobileharness.infra.client.api.proto.ServerLocatorProto.ServerLocator;
import com.google.devtools.mobileharness.infra.client.api.util.serverlocator.ServerLocatorUtil;
import com.google.devtools.mobileharness.shared.util.base.Optionals;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.Collection;
import java.util.Optional;

/** Utility class for ResourceFederationProto. */
public final class ResourceFederationUtil {

  /** Find the server locator for the given server resource type from the resource federation. */
  public static Optional<ServerLocator> findServerLocator(
      ResourceFederation resourceFederation, ServerResourceType serverResourceType) {
    return resourceFederation.getServerResourcesList().stream()
        .filter(serverResource -> serverResource.getServerResourceType().equals(serverResourceType))
        .findFirst()
        .map(ServerResource::getServerLocator);
  }

  /** Gets a map from server resource type to server locator from the resource federation. */
  public static ImmutableMap<ServerResourceType, ServerLocator> getServerMap(
      ResourceFederation resourceFederation) {
    return resourceFederation.getServerResourcesList().stream()
        .collect(
            toImmutableEnumMap(
                ServerResource::getServerResourceType, ServerResource::getServerLocator));
  }

  /**
   * Gets the resource federation from the job info if it contains a valid master server locator.
   */
  public static Optional<ResourceFederation> findResourceFederation(JobInfo jobInfo) {
    return getMasterServerLocator(jobInfo).map(ResourceFederationUtil::toResourceFederationForAts);
  }

  /**
   * Gets the resource federation from the job infos if they all have the same valid master server
   * locator.
   */
  public static Optional<ResourceFederation> findResourceFederation(Collection<JobInfo> jobInfos) {
    return findCommonMasterLocator(jobInfos)
        .map(ResourceFederationUtil::toResourceFederationForAts);
  }

  /** Converts a controller server locator a {@link ResourceFederation} for the ATS lab. */
  public static ResourceFederation toResourceFederationForAts(
      ServerLocator controllerServerLocator) {
    return ResourceFederation.newBuilder()
        .addServerResources(
            ServerResource.newBuilder()
                .setServerResourceType(ServerResourceType.MASTER)
                .setServerLocator(controllerServerLocator))
        .addServerResources(
            ServerResource.newBuilder()
                .setServerResourceType(ServerResourceType.GRPC_RELAY)
                .setServerLocator(controllerServerLocator))
        .build();
  }

  /** Returns a result only if all jobinfos have the same server locator. */
  private static Optional<ServerLocator> findCommonMasterLocator(Collection<JobInfo> jobInfos) {
    return jobInfos.stream()
        .map(ServerLocatorUtil::getMasterServerLocator)
        .reduce(Optionals::getIfEqual)
        .orElse(Optional.empty());
  }

  private ResourceFederationUtil() {}
}
