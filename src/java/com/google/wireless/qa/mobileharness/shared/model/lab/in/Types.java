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

/** Supported device types. */
public class Types {
  /** Supported device types. */
  private final Set<String> types = new HashSet<>();

  /** Add a device type. */
  @CanIgnoreReturnValue
  public synchronized Types add(String type) {
    types.add(type);
    return this;
  }

  /** Adds device types. */
  @CanIgnoreReturnValue
  public synchronized Types addAll(Collection<String> types) {
    this.types.addAll(types);
    return this;
  }

  /** Adds device types. */
  @CanIgnoreReturnValue
  public synchronized Types addAll(String[] types) {
    for (String type : types) {
      this.types.add(type);
    }
    return this;
  }

  /** Replace the current device types with the given ones. */
  @CanIgnoreReturnValue
  public synchronized Types setAll(Collection<String> types) {
    this.types.clear();
    return addAll(types);
  }

  /** Returns whether the supported device type set is empty. */
  public boolean isEmpty() {
    return types.isEmpty();
  }

  /** Returns the number of support device types. */
  public int size() {
    return types.size();
  }

  /** Gets all the supported device types. */
  public synchronized Set<String> getAll() {
    return ImmutableSet.copyOf(types);
  }

  /** Checks whether the given device type is supported. */
  public synchronized boolean support(String type) {
    return types.contains(type);
  }
}
