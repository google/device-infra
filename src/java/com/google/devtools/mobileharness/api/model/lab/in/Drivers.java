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

package com.google.devtools.mobileharness.api.model.lab.in;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.concurrent.GuardedBy;

/** Supported drivers of a device. */
public class Drivers {
  private final Object lock = new Object();

  /** Supported drivers. */
  @GuardedBy("lock")
  private final Set<String> drivers = new HashSet<>();

  /** Add a supported driver. */
  @CanIgnoreReturnValue
  public Drivers add(String driver) {
    synchronized (lock) {
      drivers.add(driver);
      return this;
    }
  }

  /** Adds supported drivers */
  @CanIgnoreReturnValue
  public Drivers addAll(Collection<String> drivers) {
    synchronized (lock) {
      this.drivers.addAll(drivers);
      return this;
    }
  }

  /** Adds supported drivers */
  @CanIgnoreReturnValue
  public Drivers addAll(String[] drivers) {
    synchronized (lock) {
      for (String driver : drivers) {
        this.drivers.add(driver);
      }
      return this;
    }
  }

  /** Replace the current supported drivers with the given ones. */
  @CanIgnoreReturnValue
  public Drivers setAll(Collection<String> drivers) {
    synchronized (lock) {
      this.drivers.clear();
      this.drivers.addAll(drivers);
      return this;
    }
  }

  public Boolean contains(String driver) {
    synchronized (lock) {
      return drivers.contains(driver);
    }
  }

  public void remove(String driver) {
    synchronized (lock) {
      drivers.remove(driver);
    }
  }

  public void clear() {
    synchronized (lock) {
      drivers.clear();
    }
  }

  /** Returns whether the supported driver set is empty. */
  public boolean isEmpty() {
    synchronized (lock) {
      return drivers.isEmpty();
    }
  }

  /** Returns the number of support drivers. */
  public int size() {
    synchronized (lock) {
      return drivers.size();
    }
  }

  /** Gets the supported drivers. */
  public ImmutableSet<String> getAll() {
    synchronized (lock) {
      return ImmutableSet.copyOf(drivers);
    }
  }

  /** Checks whether the driver is supported by this device. */
  public boolean support(String driver) {
    synchronized (lock) {
      return drivers.contains(driver);
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Drivers)) {
      return false;
    }
    Drivers that = (Drivers) obj;
    Set<String> driversCopy;
    Set<String> thatDriversCopy;
    synchronized (lock) {
      driversCopy = new HashSet<>(drivers);
    }
    synchronized (that.lock) {
      thatDriversCopy = new HashSet<>(that.drivers);
    }
    return driversCopy.equals(thatDriversCopy);
  }

  @Override
  public int hashCode() {
    synchronized (lock) {
      return drivers.hashCode();
    }
  }
}
