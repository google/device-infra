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

/** Supported decorators of a device. */
public class Decorators {
  /** Supported decorators. */
  private final Set<String> decorators = new HashSet<>();

  /** Add a supported decorator. */
  @CanIgnoreReturnValue
  public synchronized Decorators add(String decorator) {
    decorators.add(decorator);
    return this;
  }

  /** Adds supported decorators. */
  @CanIgnoreReturnValue
  public synchronized Decorators addAll(Collection<String> decorators) {
    this.decorators.addAll(decorators);
    return this;
  }

  /** Adds supported decorators. */
  @CanIgnoreReturnValue
  public synchronized Decorators addAll(String[] decorators) {
    for (String decorator : decorators) {
      this.decorators.add(decorator);
    }
    return this;
  }

  /** Replace the current supported decorators with the given ones. */
  @CanIgnoreReturnValue
  public synchronized Decorators setAll(Collection<String> decorators) {
    this.decorators.clear();
    return addAll(decorators);
  }

  /** Returns whether the supported decorator set is empty. */
  public boolean isEmpty() {
    return decorators.isEmpty();
  }

  /** Returns the number of support decorators. */
  public int size() {
    return decorators.size();
  }

  /** Gets the supported decorators. */
  public synchronized Set<String> getAll() {
    return ImmutableSet.copyOf(decorators);
  }

  /** Checks whether the decorators are supported by this device. */
  public synchronized boolean support(Collection<String> jobDecorators) {
    return this.decorators.containsAll(jobDecorators);
  }

  /** Checks the given decorators and returns the ones that are not supported by this device. */
  public synchronized Set<String> getUnsupported(Collection<String> jobDecorators) {
    Set<String> unsupportedDecorators = new HashSet<>(jobDecorators);
    unsupportedDecorators.removeAll(decorators);
    return unsupportedDecorators;
  }
}
