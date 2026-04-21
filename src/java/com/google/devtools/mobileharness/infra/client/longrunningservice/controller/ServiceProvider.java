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

package com.google.devtools.mobileharness.infra.client.longrunningservice.controller;

import com.google.common.collect.ImmutableList;
import io.grpc.BindableService;

/** Service provider for providing {@link BindableService}s. */
public interface ServiceProvider {

  /** Services for non-worker clients. */
  ImmutableList<BindableService> provideServicesForNonWorker();

  /** Services for worker clients. */
  ImmutableList<BindableService> provideServicesForWorker();

  /**
   * Services for non-worker clients provided by this service provider locally or by workers via
   * gRPC relay, depending on request metadata.
   *
   * <p>According to its definition, services in it will also appear in {@link
   * #provideServicesForNonWorker}.
   */
  ImmutableList<BindableService> provideServicesDualMode();
}
