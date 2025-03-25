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

/** Owners of a device. */
public class Owners {

  /**
   * Device owners who can config the device and run tests on the device.
   *
   * <p>Empty if the device is public.
   */
  @GuardedBy("itself")
  private final Set<String> owners = new HashSet<>();

  /** Add a device owner. */
  @CanIgnoreReturnValue
  public Owners add(String owner) {
    synchronized (owners) {
      owners.add(owner);
    }
    return this;
  }

  /** Adds device owners. */
  @CanIgnoreReturnValue
  public Owners addAll(Collection<String> owners) {
    synchronized (this.owners) {
      this.owners.addAll(owners);
    }
    return this;
  }

  /** Replace the current device owners with the given ones. */
  @CanIgnoreReturnValue
  public Owners setAll(Collection<String> owners) {
    synchronized (this.owners) {
      this.owners.clear();
      return addAll(owners);
    }
  }

  /** Returns whether the device owners is empty, which means the device is public. */
  public boolean isPublic() {
    synchronized (owners) {
      return owners.isEmpty();
    }
  }

  /** Returns the number of device owners. */
  public int size() {
    synchronized (owners) {
      return owners.size();
    }
  }

  /** Gets all the device owners, or empty if the device is public. */
  public Set<String> getAll() {
    synchronized (owners) {
      return ImmutableSet.copyOf(owners);
    }
  }

  /** Checks whether the user is in the owner list of the device or the device is public. */
  public boolean support(String user) {
    synchronized (owners) {
      return isPublic() || owners.contains(user);
    }
  }
}
