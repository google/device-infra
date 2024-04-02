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

package com.google.devtools.mobileharness.infra.ats.console.controller.sessionplugin;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginConfig;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import java.util.Optional;
import javax.annotation.Nullable;

/** {@link AtsSessionPluginConfig} and {@link AtsSessionPluginOutput} of a session. */
@AutoValue
public abstract class AtsSessionPluginConfigOutput {

  public abstract AtsSessionPluginConfig config();

  public abstract Optional<AtsSessionPluginOutput> output();

  public static AtsSessionPluginConfigOutput of(
      AtsSessionPluginConfig config, @Nullable AtsSessionPluginOutput output) {
    return new AutoValue_AtsSessionPluginConfigOutput(config, Optional.ofNullable(output));
  }
}
