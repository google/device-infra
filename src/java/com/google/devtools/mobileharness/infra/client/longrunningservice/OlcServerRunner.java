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

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;

/** Runner for running main logic of OLC server. */
public interface OlcServerRunner {

  /**
   * Runs basic logic for both standalone mode and embedded mode.
   *
   * <p>Major ones include:
   *
   * <ul>
   *   <li>Initializes OmniLab client API.
   *   <li>Initializes exec mode (e.g., local device manager).
   *   <li>Starts session manager.
   * </ul>
   */
  void runBasic() throws MobileHarnessException;

  /**
   * Runs logic for standalone mode, in which the server is not embedded in another component.
   *
   * <p>Major ones include:
   *
   * <ul>
   *   <li>Starts streaming log manager.
   *   <li>Connects to database.
   *   <li>Starts gRPC servers.
   *   <li>Starts monitoring.
   *   <li>Resumes unfinished sessions.
   * </ul>
   */
  void runStandaloneMode() throws MobileHarnessException;

  void awaitTermination() throws InterruptedException;

  void onShutdown();
}
