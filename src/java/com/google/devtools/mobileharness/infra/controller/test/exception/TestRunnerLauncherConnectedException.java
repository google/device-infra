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

package com.google.devtools.mobileharness.infra.controller.test.exception;

import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;

/**
 * Signals that an attempt to connect a test runner to a test runner launcher which has already
 * connected to another test runner.
 */
public class TestRunnerLauncherConnectedException extends MobileHarnessException {

  public TestRunnerLauncherConnectedException(String message) {
    super(InfraErrorId.TM_TEST_LAUNCHER_CONNECTED, message);
  }
}
