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

package com.google.wireless.qa.mobileharness.shared.model.lab.in;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.concurrent.GuardedBy;

/** Executors of a device. */
public class Executors {

  /**
   * Device executors who can run tests on the device.
   *
   * <p>NOTE that device owners are also able to run tests on device.
   */
  @GuardedBy("itself")
  private final Set<String> executors = new HashSet<>();

  /** Add a device executor. */
  @CanIgnoreReturnValue
  public Executors add(String executor) {
    synchronized (executors) {
      executors.add(executor);
    }
    return this;
  }

  /** Adds device executors. */
  @CanIgnoreReturnValue
  public Executors addAll(Collection<String> executors) {
    synchronized (this.executors) {
      this.executors.addAll(executors);
    }
    return this;
  }

  /** Replace the current device executors with the given ones. */
  @CanIgnoreReturnValue
  public Executors setAll(Collection<String> executors) {
    synchronized (this.executors) {
      this.executors.clear();
      return addAll(executors);
    }
  }

  /** Returns the number of device executors. */
  public int size() {
    synchronized (executors) {
      return executors.size();
    }
  }

  /** Gets all the device executors. */
  public Set<String> getAll() {
    synchronized (executors) {
      return ImmutableSet.copyOf(executors);
    }
  }

  /** Checks whether the user is in the executor list of the device. */
  public boolean support(String user) {
    synchronized (executors) {
      return executors.contains(user);
    }
  }
}
