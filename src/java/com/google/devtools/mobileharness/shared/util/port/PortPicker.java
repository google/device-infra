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

package com.google.devtools.mobileharness.shared.util.port;

import java.io.IOException;

/**
 * Branched from {@code com.google.testing.serverutil.PortPicker}.
 *
 * <p>{@code PortProber} only exposes static methods. Using this wrapper around it allows tests to
 * fake out that dependency and avoid the heavy-weight static initialization.
 *
 * <p>These instance methods are defined in a separate class from {@code PortProber} to avoid static
 * initialization.
 *
 * <p>Instances of this class are thread-safe only if the {@code pickUnusedPort} methods on {@code
 * PortProber} are thread-safe.
 */
public class PortPicker implements PortFinder {
  @Override
  public int pickUnusedPort() throws UnableToFindPortException {
    try {
      return PortProber.pickUnusedPort();
    } catch (IOException e) {
      throw new UnableToFindPortException(e);
    } catch (InterruptedException e) {
      // Ensure the thread's interrupt status is preserved.
      Thread.currentThread().interrupt();
      throw new UnableToFindPortException(e);
    }
  }
}
