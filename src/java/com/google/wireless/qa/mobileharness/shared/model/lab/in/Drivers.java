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

/** Supported drivers of a device. */
public class Drivers {
  /** Supported drivers. */
  private final Set<String> drivers = new HashSet<>();

  /** Add a supported driver. */
  @CanIgnoreReturnValue
  public synchronized Drivers add(String driver) {
    drivers.add(driver);
    return this;
  }

  /** Adds supported drivers */
  @CanIgnoreReturnValue
  public synchronized Drivers addAll(Collection<String> drivers) {
    this.drivers.addAll(drivers);
    return this;
  }

  /** Adds supported drivers */
  @CanIgnoreReturnValue
  public synchronized Drivers addAll(String[] drivers) {
    for (String driver : drivers) {
      this.drivers.add(driver);
    }
    return this;
  }

  /** Replace the current supported drivers with the given ones. */
  @CanIgnoreReturnValue
  public synchronized Drivers setAll(Collection<String> drivers) {
    this.drivers.clear();
    this.drivers.addAll(drivers);
    return this;
  }

  /** Returns whether the supported driver set is empty. */
  public boolean isEmpty() {
    return drivers.isEmpty();
  }

  /** Returns the number of support drivers. */
  public int size() {
    return drivers.size();
  }

  /** Gets the supported drivers. */
  public synchronized Set<String> getAll() {
    return ImmutableSet.copyOf(drivers);
  }

  /** Checks whether the driver is supported by this device. */
  public synchronized boolean support(String driver) {
    return drivers.contains(driver);
  }
}
