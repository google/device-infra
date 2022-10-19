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

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Required dimensions for device allocations. Only the devices with all these dimensions can be
 * allocated.
 */
public class Dimensions {

  /** Required dimensions of the target devices. */
  private final Map<String, String> dimensions = new ConcurrentHashMap<>();

  /** Creates the dimensions of a job. */
  public Dimensions() {}

  /**
   * Adds the given dimension. If there is another dimension with the same name exists, it will be
   * overwritten.
   */
  @CanIgnoreReturnValue
  public Dimensions add(String name, String value) {
    dimensions.put(name, value);
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
    return add(name == null ? null : name.name().toLowerCase(), value);
  }

  /** Adds the given dimension if the dimension name doesn't already exist. */
  @CanIgnoreReturnValue
  public Dimensions addIfAbsent(String name, String value) {
    dimensions.putIfAbsent(name, value);
    return this;
  }

  /**
   * Adds the given dimension if the dimension name doesn't already exist.
   *
   * <p>The name of the dimension is as the {@code name.name().toLowerCase()}.
   */
  @CanIgnoreReturnValue
  public Dimensions addIfAbsent(Dimension.Name name, String value) {
    return addIfAbsent(name == null ? null : name.name().toLowerCase(), value);
  }

  /** Adds all the given dimensions. */
  @CanIgnoreReturnValue
  public Dimensions addAll(Map<String, String> dimensions) {
    this.dimensions.putAll(dimensions);
    return this;
  }

  /** Removes the given dimension. Does nothing if it doesn't exist. */
  @CanIgnoreReturnValue
  public Dimensions remove(String name) {
    this.dimensions.remove(name);
    return this;
  }

  /** Removes the given dimension. Does nothing if it doesn't exist. */
  @CanIgnoreReturnValue
  public Dimensions remove(Dimension.Name name) {
    return remove(name.name().toLowerCase());
  }

  /** Returns whether the dimension map is empty. */
  public boolean isEmpty() {
    return dimensions.isEmpty();
  }

  /** Returns the number of dimension pairs. */
  public int size() {
    return dimensions.size();
  }

  /** Returns the {name, value} mapping of all the dimensions. */
  public ImmutableMap<String, String> getAll() {
    return ImmutableMap.copyOf(dimensions);
  }

  /** Gets the dimension value of the given dimension name. */
  public Optional<String> get(String name) {
    return Optional.ofNullable(dimensions.get(name));
  }

  /** Gets the dimension value of the given dimension name. */
  public Optional<String> get(Dimension.Name name) {
    return get(name.name().toLowerCase());
  }
}
