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

package com.google.devtools.mobileharness.fe.v6.service.shared.auth;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;

/** Provider for checking user group membership. */
public interface GroupMembershipProvider {

  /** Returns true if the given user is a member of any of the given groups. */
  ListenableFuture<Boolean> isMemberOfAny(String username, List<String> groupNames);

  /**
   * Returns true if the given name is a valid user or mdb group.
   *
   * <p>This is used for pre-save validation of owner/executor names. Implementations should return
   * true if the name exists and false if it doesn't. If the underlying service is unavailable,
   * implementations should return true to avoid blocking saves.
   */
  ListenableFuture<Boolean> exists(String name);
}
