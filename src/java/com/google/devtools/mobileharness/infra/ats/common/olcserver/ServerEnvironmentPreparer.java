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

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.nio.file.Path;

/** Preparer for preparing environment to run OLC server. */
public interface ServerEnvironmentPreparer {

  /** Prepares environment to run OLC server. */
  ServerEnvironment prepareServerEnvironment() throws MobileHarnessException, InterruptedException;

  /** Environment to run OLC server. */
  @AutoValue
  abstract class ServerEnvironment {

    public abstract Path serverBinary();

    public abstract Path javaBinary();

    public static ServerEnvironment of(Path serverBinary, Path javaBinary) {
      return new AutoValue_ServerEnvironmentPreparer_ServerEnvironment(serverBinary, javaBinary);
    }
  }

  /** A no-op implementation of {@link ServerEnvironmentPreparer}. */
  class NoOpServerEnvironmentPreparer implements ServerEnvironmentPreparer {

    private final ServerEnvironment serverEnvironment;

    public NoOpServerEnvironmentPreparer(ServerEnvironment serverEnvironment) {
      this.serverEnvironment = serverEnvironment;
    }

    @Override
    public ServerEnvironment prepareServerEnvironment() {
      return serverEnvironment;
    }
  }
}
