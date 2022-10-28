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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.proto.Common.StrPair;
import java.util.Collection;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

/** Supported dimensions of a device. */
public class Dimensions {

  /** Supported dimensions. */
  private final ListMultimap<String, String> dimensions =
      MultimapBuilder.hashKeys().arrayListValues().build();

  /** Adds a dimension pair with the given name and value. */
  @CanIgnoreReturnValue
  public synchronized Dimensions add(String name, String value) {
    dimensions.put(name, value);
    return this;
  }

  /** Adds a dimension pair with the given name and value. */
  @CanIgnoreReturnValue
  public Dimensions add(Dimension.Name name, String value) {
    add(name.name().toLowerCase(), value);
    return this;
  }

  /** Adds all the given dimensions. */
  @CanIgnoreReturnValue
  public synchronized Dimensions addAll(Iterable<StrPair> dimensions) {
    dimensions.forEach(strPair -> this.dimensions.put(strPair.getName(), strPair.getValue()));
    return this;
  }

  /** Adds all the given dimensions. */
  @CanIgnoreReturnValue
  public synchronized Dimensions addAll(Multimap<String, String> dimensions) {
    this.dimensions.putAll(dimensions);
    return this;
  }

  /** Replace the current dimensions with the given ones. */
  @CanIgnoreReturnValue
  public synchronized Dimensions setAll(Iterable<StrPair> dimensions) {
    this.dimensions.clear();
    return addAll(dimensions);
  }

  /** Replace the current dimensions with the given ones. */
  @CanIgnoreReturnValue
  public synchronized Dimensions setAll(Multimap<String, String> dimensions) {
    this.dimensions.clear();
    return addAll(dimensions);
  }

  /** Returns whether the dimension map is empty. */
  public boolean isEmpty() {
    return dimensions.isEmpty();
  }

  /** Returns the number of dimension pairs. */
  public int size() {
    return dimensions.size();
  }

  /** Gets the dimension values of the given dimension name. */
  public synchronized Collection<String> get(String name) {
    return ImmutableList.copyOf(dimensions.get(name));
  }

  /** Gets the dimension value of the given dimension name. */
  public Collection<String> get(Dimension.Name name) {
    return get(name.name().toLowerCase());
  }

  /**
   * Gets the first dimension value of the given dimension name, or the default value if there is no
   * dimension with the given name.
   */
  public String getFirst(String name, String defaultValue) {
    return Iterables.getFirst(get(name), defaultValue);
  }

  /**
   * Gets the first dimension value of the given dimension name, or the default value if there is no
   * dimension with the given name.
   */
  public String getFirst(Dimension.Name name, String defaultValue) {
    return getFirst(name.name().toLowerCase(), defaultValue);
  }

  /**
   * Gets the only dimension value of the given dimension name.
   *
   * @throws NoSuchElementException if there is no dimension with the given name
   * @throws IllegalArgumentException if there are multiple dimensions with the given name
   */
  public String getOnly(String name) {
    return Iterables.getOnlyElement(get(name));
  }

  /**
   * Gets the only dimension value of the given dimension name, or the default value if there is no
   * dimension with the given name.
   *
   * @throws IllegalArgumentException if there are multiple dimensions with the given name
   */
  public String getOnly(String name, String defaultValue) {
    return Iterables.getOnlyElement(get(name), defaultValue);
  }

  /**
   * Gets the only dimension value of the given dimension name.
   *
   * @throws NoSuchElementException if there is no dimension with the given name
   * @throws IllegalArgumentException if there are multiple dimensions with the given name
   */
  public String getOnly(Dimension.Name name) {
    return getOnly(name.name().toLowerCase());
  }

  /**
   * Gets the only dimension value of the given dimension name, or the default value if there is no
   * dimension with the given name.
   *
   * @throws IllegalArgumentException if there are multiple dimensions with the given name
   */
  public String getOnly(Dimension.Name name, String defaultValue) {
    return getOnly(name.name().toLowerCase(), defaultValue);
  }

  /** Gets a copy of all the supported dimensions. */
  public synchronized ListMultimap<String, String> getAll() {
    return getAll(new HashSet<>());
  }

  /**
   * Gets a copy of all the supported dimensions, except those with names in the given set.
   *
   * @param ignoredNames the set to remove dimensions from the result if the names of which are in
   *     it
   */
  public synchronized ListMultimap<String, String> getAll(Set<String> ignoredNames) {
    ListMultimap<String, String> filteredDimensions =
        MultimapBuilder.hashKeys().arrayListValues().build(dimensions);
    dimensions
        .asMap()
        .forEach(
            (name, values) -> {
              if (ignoredNames.contains(name)) {
                filteredDimensions.removeAll(name);
              }
            });
    return filteredDimensions;
  }
}
