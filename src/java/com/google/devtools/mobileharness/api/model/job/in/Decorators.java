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

package com.google.devtools.mobileharness.api.model.job.in;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Job required decorators for a device.
 *
 * <p>The decorators are in reversed decorating order and in executing order, which means the
 * preRun() of a decorator in the front will be executed <b>BEFORE</b> preRun() of other decorators.
 *
 * <p>TODO: Different sub-devices may have different param values(the difference between
 * CompositeDeviceDecoratorAdapter and MoblyDecoratorAdapter). Need to add an ScopedSpecs field to
 * this class to hold it.
 */
public class Decorators {

  private final List<String> decorators = new ArrayList<>();

  /** Add a required decorator. */
  @CanIgnoreReturnValue
  public synchronized Decorators add(String decorator) {
    decorators.add(decorator);
    return this;
  }

  /** Adds required decorators. */
  @CanIgnoreReturnValue
  public synchronized Decorators addAll(List<String> decorators) {
    this.decorators.addAll(decorators);
    return this;
  }

  /** Adds required decorators. */
  @CanIgnoreReturnValue
  public synchronized Decorators addAll(String[] decorators) {
    this.decorators.addAll(Arrays.asList(decorators));
    return this;
  }

  /** Replaces the current required decorators with the given ones. */
  @CanIgnoreReturnValue
  public synchronized Decorators setAll(List<String> decorators) {
    this.decorators.clear();
    addAll(decorators);
    return this;
  }

  /** Returns whether the required decorator list is empty. */
  public boolean isEmpty() {
    return decorators.isEmpty();
  }

  /** Returns the number of required decorators. */
  public int size() {
    return decorators.size();
  }

  /** Gets the required decorators. */
  public synchronized List<String> getAll() {
    return ImmutableList.copyOf(decorators);
  }
}
