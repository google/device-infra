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

/** Supported decorators of a device. */
public class Decorators {
  /** Supported decorators. */
  @GuardedBy("lock")
  private final Set<String> decorators = new HashSet<>();

  private final Object lock = new Object();

  /** Add a supported decorator. */
  @CanIgnoreReturnValue
  public Decorators add(String decorator) {
    synchronized (lock) {
      decorators.add(decorator);
      return this;
    }
  }

  /** Adds supported decorators. */
  @CanIgnoreReturnValue
  public Decorators addAll(Collection<String> decorators) {
    synchronized (lock) {
      this.decorators.addAll(decorators);
      return this;
    }
  }

  /** Adds supported decorators. */
  @CanIgnoreReturnValue
  public Decorators addAll(String[] decorators) {
    synchronized (lock) {
      for (String decorator : decorators) {
        this.decorators.add(decorator);
      }
      return this;
    }
  }

  /** Replace the current supported decorators with the given ones. */
  @CanIgnoreReturnValue
  public Decorators setAll(Collection<String> decorators) {
    synchronized (lock) {
      this.decorators.clear();
      return addAll(decorators);
    }
  }

  public boolean contains(String decorator) {
    synchronized (lock) {
      return decorators.contains(decorator);
    }
  }

  public void remove(String decorator) {
    synchronized (lock) {
      decorators.remove(decorator);
    }
  }

  public void clear() {
    synchronized (lock) {
      decorators.clear();
    }
  }

  /** Returns whether the supported decorator set is empty. */
  public boolean isEmpty() {
    synchronized (lock) {
      return decorators.isEmpty();
    }
  }

  /** Returns the number of support decorators. */
  public int size() {
    synchronized (lock) {
      return decorators.size();
    }
  }

  /** Gets the supported decorators. */
  public ImmutableSet<String> getAll() {
    synchronized (lock) {
      return ImmutableSet.copyOf(decorators);
    }
  }

  /** Checks whether the decorators are supported by this device. */
  public boolean support(Collection<String> jobDecorators) {
    synchronized (lock) {
      return this.decorators.containsAll(jobDecorators);
    }
  }

  /** Checks the given decorators and returns the ones that are not supported by this device. */
  public Set<String> getUnsupported(Collection<String> jobDecorators) {
    Set<String> unsupportedDecorators = new HashSet<>(jobDecorators);
    synchronized (lock) {
      unsupportedDecorators.removeAll(decorators);
    }
    return unsupportedDecorators;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Decorators)) {
      return false;
    }
    Decorators that = (Decorators) obj;
    Set<String> decoratorsCopy;
    Set<String> thatDecoratorsCopy2;
    synchronized (lock) {
      decoratorsCopy = new HashSet<>(decorators);
    }
    synchronized (that.lock) {
      thatDecoratorsCopy2 = new HashSet<>(that.decorators);
    }
    return decoratorsCopy.equals(thatDecoratorsCopy2);
  }

  @Override
  public int hashCode() {
    synchronized (lock) {
      return decorators.hashCode();
    }
  }
}
