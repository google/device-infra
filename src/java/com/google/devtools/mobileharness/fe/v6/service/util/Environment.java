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

package com.google.devtools.mobileharness.fe.v6.service.util;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Utility class to determine the runtime environment. */
@Singleton
public class Environment {

  /** The environment type of the current runtime. */
  public enum EnvironmentType {
    GOOGLE_INTERNAL,
    ATS
  }

  private static final EnvironmentType CURRENT_ENVIRONMENT = checkEnvironment();

  private static EnvironmentType checkEnvironment() {
    boolean isGoogleInternal = false;
    return isGoogleInternal ? EnvironmentType.GOOGLE_INTERNAL : EnvironmentType.ATS;
  }

  @Inject
  Environment() {}

  /** Returns the current runtime environment type. */
  public EnvironmentType getEnvironmentType() {
    return CURRENT_ENVIRONMENT;
  }

  /** Helper method for boolean checks. */
  public boolean isGoogleInternal() {
    return CURRENT_ENVIRONMENT == EnvironmentType.GOOGLE_INTERNAL;
  }

  /** Helper method for boolean checks. */
  public boolean isAts() {
    return CURRENT_ENVIRONMENT == EnvironmentType.ATS;
  }
}
