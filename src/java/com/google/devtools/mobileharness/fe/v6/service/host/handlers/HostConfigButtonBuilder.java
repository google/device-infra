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
import com.google.devtools.mobileharness.fe.v6.service.host.util.HostActionButtonCreator;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.ActionButtonState;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManagerFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Utility class to build {@link ActionButtonState} for host config button. */
@Singleton
public class HostConfigButtonBuilder {

  private final HostActionButtonCreator hostActionButtonCreator;
  private final FeatureManagerFactory featureManagerFactory;

  @Inject
  HostConfigButtonBuilder(
      HostActionButtonCreator hostActionButtonCreator,
      FeatureManagerFactory featureManagerFactory) {
    this.hostActionButtonCreator = hostActionButtonCreator;
    this.featureManagerFactory = featureManagerFactory;
  }

  public ActionButtonState build(
      UniverseScope universe, Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {

    BooleanSupplier buttonVisibleSupplier =
        () -> featureManagerFactory.create(universe).isConfigurationFeatureEnabled();

    return hostActionButtonCreator.buildButton(
        labInfoOpt.orElse(LabInfo.getDefaultInstance()),
        labTypeOpt.orElse(""),
        buttonVisibleSupplier,
        () -> true,
        () -> true,
        "Configure the host configuration");
  }
}
