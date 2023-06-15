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

/** Device types of a device. */
public class Types {
  private final Object lock = new Object();

  /** Supported types. */
  @GuardedBy("lock")
  private final Set<String> types = new HashSet<>();

  /** Add a supported driver. */
  @CanIgnoreReturnValue
  public Types add(String driver) {
    synchronized (lock) {
      types.add(driver);
      return this;
    }
  }

  /** Adds supported types */
  @CanIgnoreReturnValue
  public Types addAll(Collection<String> types) {
    synchronized (lock) {
      this.types.addAll(types);
      return this;
    }
  }

  /** Adds supported types */
  @CanIgnoreReturnValue
  public Types addAll(String[] types) {
    synchronized (lock) {
      for (String driver : types) {
        this.types.add(driver);
      }
      return this;
    }
  }

  /** Replace the current supported types with the given ones. */
  @CanIgnoreReturnValue
  public Types setAll(Collection<String> types) {
    synchronized (lock) {
      this.types.clear();
      this.types.addAll(types);
      return this;
    }
  }

  public Boolean contains(String driver) {
    synchronized (lock) {
      return types.contains(driver);
    }
  }

  public void remove(String driver) {
    synchronized (lock) {
      types.remove(driver);
    }
  }

  public void clear() {
    synchronized (lock) {
      types.clear();
    }
  }

  /** Returns whether the supported driver set is empty. */
  public boolean isEmpty() {
    synchronized (lock) {
      return types.isEmpty();
    }
  }

  /** Returns the number of support types. */
  public int size() {
    synchronized (lock) {
      return types.size();
    }
  }

  /** Gets the supported types. */
  public ImmutableSet<String> getAll() {
    synchronized (lock) {
      return ImmutableSet.copyOf(types);
    }
  }

  /** Checks whether the driver is supported by this device. */
  public boolean support(String driver) {
    synchronized (lock) {
      return types.contains(driver);
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Types)) {
      return false;
    }
    Types that = (Types) obj;
    Set<String> typesCopy;
    Set<String> thatTypesCopy;
    synchronized (lock) {
      typesCopy = new HashSet<>(types);
    }
    synchronized (that.lock) {
      thatTypesCopy = new HashSet<>(that.types);
    }
    return typesCopy.equals(thatTypesCopy);
  }

  @Override
  public int hashCode() {
    synchronized (lock) {
      return types.hashCode();
    }
  }
}
