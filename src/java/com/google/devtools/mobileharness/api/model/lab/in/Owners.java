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

/** Owners of a device. */
public class Owners {
  /** Device owners. Or empty if the device is public. */
  private final Set<String> owners = new HashSet<>();

  /** Add a device owner. */
  @CanIgnoreReturnValue
  public synchronized Owners add(String owner) {
    owners.add(owner);
    return this;
  }

  /** Adds device owners. */
  @CanIgnoreReturnValue
  public synchronized Owners addAll(Collection<String> owners) {
    this.owners.addAll(owners);
    return this;
  }

  /** Adds device owners. */
  @CanIgnoreReturnValue
  public synchronized Owners addAll(String[] owners) {
    for (String owner : owners) {
      this.owners.add(owner);
    }
    return this;
  }

  /** Replace the current device owners with the given ones. */
  @CanIgnoreReturnValue
  public synchronized Owners setAll(Collection<String> owners) {
    this.owners.clear();
    return addAll(owners);
  }

  /** Returns whether the device owners is empty, which means the device is public. */
  public boolean isPublic() {
    return owners.isEmpty();
  }

  /** Returns the number of device owners. */
  public int size() {
    return owners.size();
  }

  /** Gets all the device owners, or empty if the device is public. */
  public synchronized Set<String> getAll() {
    return ImmutableSet.copyOf(owners);
  }

  /** Checks whether the user is in the owner list of the device. */
  public synchronized boolean support(String user) {
    return owners.isEmpty() || owners.contains(user);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Owners)) {
      return false;
    }
    Owners that = (Owners) obj;
    return owners.equals(that.owners);
  }

  @Override
  public int hashCode() {
    return owners.hashCode();
  }
}
