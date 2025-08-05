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

package com.google.devtools.mobileharness.infra.client.longrunningservice;

import com.google.inject.Module;
import java.time.Instant;

/**
 * Implementation of {@link OlcServerModuleFactory}.
 *
 * @apiNote this class must have a no-arg constructor or can be injected by Guice without module.
 */
public class OlcServerModuleFactoryImpl implements OlcServerModuleFactory {

  @Override
  public Module create(
      boolean isAtsMode,
      Instant serverStartTime,
      boolean enableCloudPubsubMonitoring,
      boolean enableDatabase,
      boolean enableGrpcRelay) {
    return new OlcServerModule(
        /* isAtsMode= */ isAtsMode,
        /* serverStartTime= */ serverStartTime,
        /* enableCloudPubsubMonitoring= */ enableCloudPubsubMonitoring,
        /* enableDatabase= */ enableDatabase,
        /* enableGrpcRelay= */ enableGrpcRelay);
  }
}
