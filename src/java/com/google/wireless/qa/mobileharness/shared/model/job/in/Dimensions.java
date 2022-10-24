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

package com.google.wireless.qa.mobileharness.shared.model.job.in;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Required dimensions for device allocations. Only the devices with all these dimensions can be
 * allocated.
 */
public class Dimensions {

  /** The time records. */
  @Nullable private final Timing timing;

  private final com.google.devtools.mobileharness.api.model.job.in.Dimensions newDimensions;

  /** Creates the dimension segment. */
  public Dimensions(@Nullable Timing timing) {
    this(timing, new com.google.devtools.mobileharness.api.model.job.in.Dimensions());
  }

  /** Creates the dimension segment. */
  Dimensions(
      @Nullable Timing timing,
      com.google.devtools.mobileharness.api.model.job.in.Dimensions newDimensions) {
    this.timing = timing;
    this.newDimensions = newDimensions;
  }

  public com.google.devtools.mobileharness.api.model.job.in.Dimensions toNewDimensions() {
    return newDimensions;
  }

  /**
   * Adds the given dimension. If there is another dimension with the same name exists, it will be
   * overwritten.
   */
  @CanIgnoreReturnValue
  public Dimensions add(String name, String value) {
    newDimensions.add(name, value);
    if (timing != null) {
      timing.touch();
    }
    return this;
  }

  /**
   * Adds the given dimension. If there is another dimension with the same name exists, it will be
   * overwritten.
   *
   * <p>The name of the dimension is as the {@code name.name().toLowerCase()}.
   */
  @CanIgnoreReturnValue
  public Dimensions add(Dimension.Name name, String value) {
    newDimensions.add(name, value);
    if (timing != null) {
      timing.touch();
    }
    return this;
  }

  /** Adds the given dimension if the dimension name doesn't already exist. */
  @CanIgnoreReturnValue
  public Dimensions addIfAbsent(String name, String value) {
    newDimensions.addIfAbsent(name, value);
    if (timing != null) {
      timing.touch();
    }
    return this;
  }

  /**
   * Adds the given dimension if the dimension name doesn't already exist.
   *
   * <p>The name of the dimension is as the {@code name.name().toLowerCase()}.
   */
  @CanIgnoreReturnValue
  public Dimensions addIfAbsent(Dimension.Name name, String value) {
    newDimensions.addIfAbsent(name, value);
    if (timing != null) {
      timing.touch();
    }
    return this;
  }

  /** Adds all the given dimensions. */
  @CanIgnoreReturnValue
  public Dimensions addAll(Map<String, String> dimensions) {
    newDimensions.addAll(dimensions);
    if (timing != null) {
      timing.touch();
    }
    return this;
  }

  /**
   * Removes the given dimension. Does nothing if it doesn't exist.
   *
   * <p>The name of the dimension is as the {@code name.name().toLowerCase()}.
   */
  @CanIgnoreReturnValue
  public Dimensions remove(String name) {
    newDimensions.remove(name);
    if (timing != null) {
      timing.touch();
    }
    return this;
  }

  /**
   * Removes the given dimension. Does nothing if it doesn't exist.
   *
   * <p>The name of the dimension is as the {@code name.name().toLowerCase()}.
   */
  @CanIgnoreReturnValue
  public Dimensions remove(Dimension.Name name) {
    newDimensions.remove(name);
    if (timing != null) {
      timing.touch();
    }
    return this;
  }

  /** Returns whether the dimension map is empty. */
  public boolean isEmpty() {
    return newDimensions.isEmpty();
  }

  /** Returns the number of dimension pairs. */
  public int size() {
    return newDimensions.size();
  }

  /** Returns the {name, value} mapping of all the dimensions. */
  public ImmutableMap<String, String> getAll() {
    return newDimensions.getAll();
  }

  /** Gets the dimension value of the given dimension name. */
  @Nullable
  public String get(String name) {
    return newDimensions.get(name).orElse(null);
  }

  /** Gets the dimension value of the given dimension name. */
  @Nullable
  public String get(Dimension.Name name) {
    return newDimensions.get(name).orElse(null);
  }
}
