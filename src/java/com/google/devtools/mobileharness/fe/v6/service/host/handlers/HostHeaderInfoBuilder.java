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

import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostActions;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostHeaderInfo;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Builder for {@link HostHeaderInfo}. */
@Singleton
public class HostHeaderInfoBuilder {

  private final HostConfigButtonBuilder hostConfigButtonBuilder;
  private final HostDebugButtonBuilder hostDebugButtonBuilder;
  private final HostDecommissionButtonBuilder hostDecommissionButtonBuilder;

  @Inject
  HostHeaderInfoBuilder(
      HostConfigButtonBuilder hostConfigButtonBuilder,
      HostDebugButtonBuilder hostDebugButtonBuilder,
      HostDecommissionButtonBuilder hostDecommissionButtonBuilder) {
    this.hostConfigButtonBuilder = hostConfigButtonBuilder;
    this.hostDebugButtonBuilder = hostDebugButtonBuilder;
    this.hostDecommissionButtonBuilder = hostDecommissionButtonBuilder;
  }

  /** Builds {@link HostHeaderInfo} based on host name, universe and lab info. */
  public HostHeaderInfo build(
      String hostName,
      UniverseScope universe,
      Optional<LabInfo> labInfoOpt,
      Optional<String> labTypeOpt) {
    return HostHeaderInfo.newBuilder()
        .setHostName(hostName)
        .setActions(
            HostActions.newBuilder()
                .setConfiguration(hostConfigButtonBuilder.build(universe, labInfoOpt, labTypeOpt))
                .setDebug(hostDebugButtonBuilder.build(universe, labInfoOpt, labTypeOpt))
                .setDecommission(
                    hostDecommissionButtonBuilder.build(universe, labInfoOpt, labTypeOpt)))
        .build();
  }
}
