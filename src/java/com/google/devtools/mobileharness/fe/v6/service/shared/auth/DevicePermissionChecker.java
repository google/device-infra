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

/*
 * Copyright 2026 Google LLC
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

package com.google.devtools.mobileharness.fe.v6.service.shared.auth;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Helper to check device access permissions. */
@Singleton
public class DevicePermissionChecker {

  private final GroupMembershipProvider groupMembershipProvider;
  private final ListeningExecutorService executor;

  @VisibleForTesting
  @Inject
  public DevicePermissionChecker(
      GroupMembershipProvider groupMembershipProvider, ListeningExecutorService executor) {
    this.groupMembershipProvider = groupMembershipProvider;
    this.executor = executor;
  }

  /** Checks if the user has permission to access the device. */
  public ListenableFuture<Boolean> hasPermission(String username, DeviceInfo deviceInfo) {
    return Futures.transform(
        calculateRunAsCandidates(username, deviceInfo),
        candidates -> !candidates.isEmpty(),
        directExecutor());
  }

  /**
   * Calculates the "run as" candidates for the user on the device. If the returned list is empty,
   * the user has no permission.
   */
  public ListenableFuture<ImmutableList<String>> calculateRunAsCandidates(
      String username, DeviceInfo deviceInfo) {
    ImmutableList<String> ownersAndExecutors =
        ImmutableList.<String>builder()
            .addAll(deviceInfo.getDeviceFeature().getOwnerList())
            .addAll(deviceInfo.getDeviceFeature().getExecutorList())
            .build();
    return calculateRunAsCandidates(username, ownersAndExecutors);
  }

  /** Calculates the "run as" candidates based on owners and executors list. */
  public ListenableFuture<ImmutableList<String>> calculateRunAsCandidates(
      String username, List<String> ownersAndExecutors) {
    if (username.isEmpty()) {
      return immediateFuture(ImmutableList.of());
    }

    if (ownersAndExecutors.isEmpty()) {
      return immediateFuture(ImmutableList.of(username));
    }

    // Deduplicate candidates to minimize RPC calls.
    ImmutableSet<String> uniqueCandidates = ImmutableSet.copyOf(ownersAndExecutors);

    return Futures.transform(
        Futures.allAsList(
            uniqueCandidates.stream()
                .map(
                    candidate ->
                        candidate.equals(username)
                            ? immediateFuture(username)
                            : Futures.transform(
                                groupMembershipProvider.isMemberOfAny(
                                    username, ImmutableList.of(candidate)),
                                isMember -> isMember ? candidate : null,
                                directExecutor()))
                .collect(toImmutableList())),
        results -> results.stream().filter(Objects::nonNull).collect(toImmutableList()),
        executor);
  }
}
