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

package com.google.devtools.mobileharness.infra.ats.common.olcserver;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.inject.Qualifier;

/** Annotations for ATS console / local runner that connect to ATS OLC server. */
public class Annotations {

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface ClientComponentName {}

  /** ID of the ATS console / local runner. */
  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  public @interface ClientId {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface ServerChannel {}

  /** OLC server stub for ATS console / local runner. */
  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  public @interface ServerStub {

    /** Type of {@link ServerStub}. */
    enum Type {
      CONTROL_SERVICE,
      SESSION_SERVICE,
      VERSION_SERVICE,
    }

    Type value();
  }

  /** Flags of OLC server and its caller (ATS console / local runner). */
  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  public @interface DeviceInfraServiceFlags {}

  private Annotations() {}
}
