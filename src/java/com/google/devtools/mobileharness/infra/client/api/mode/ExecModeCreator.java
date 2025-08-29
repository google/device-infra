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

package com.google.devtools.mobileharness.infra.client.api.mode;

import static com.google.common.base.Ascii.equalsIgnoreCase;
import static com.google.devtools.mobileharness.infra.client.api.mode.ExecModeUtil.createRemoteModeInstance;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.api.proto.ResourceFederationProto.ResourceFederation;
import com.google.devtools.mobileharness.infra.client.api.util.resourcefederation.ResourceFederationUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.Collection;
import java.util.Optional;

/** Utility methods to create {@code ExecMode} instances. */
public final class ExecModeCreator {

  /**
   * Creates {@code ExecMode} instance according to the exec mode name and a collection of {@code
   * JobInfo}.
   *
   * @throws MobileHarnessException if {@code ExecMode} instance cannot be created.
   */
  public static ExecMode createInstance(String modeName, Collection<JobInfo> jobInfos)
      throws MobileHarnessException {
    Optional<ResourceFederation> resourceFederation =
        ResourceFederationUtil.findResourceFederation(jobInfos);
    if (resourceFederation.isPresent() && equalsIgnoreCase(modeName, "REMOTE")) {
      return createRemoteModeInstance(modeName, resourceFederation.get());
    } else {
      return ExecModeUtil.createInstance(modeName);
    }
  }

  private ExecModeCreator() {}
}
