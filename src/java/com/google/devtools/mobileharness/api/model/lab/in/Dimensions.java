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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Multimaps.toMultimap;
import static com.google.common.collect.Streams.stream;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.proto.Common.StrPair;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import javax.annotation.concurrent.ThreadSafe;

/** Supported/required dimensions of a device. */
@ThreadSafe
public interface Dimensions {

  /** Adds a dimension pair with the given name and value. */
  Dimensions add(String name, String value);

  /** Adds a dimension pair with the given name and value. */
  default Dimensions add(Dimension.Name name, String value) {
    return add(Ascii.toLowerCase(name.name()), value);
  }

  /** Adds a dimension. */
  default Dimensions add(DeviceDimension dimension) {
    return add(dimension.getName(), dimension.getValue());
  }

  /** Adds all the given dimensions. */
  Dimensions addAll(Multimap<String, String> dimensions);

  /** Adds all the given dimensions. */
  default Dimensions addAll(Iterable<StrPair> dimensions) {
    return addAll(
        stream(dimensions)
            .collect(
                toMultimap(
                    StrPair::getName,
                    StrPair::getValue,
                    () -> MultimapBuilder.hashKeys().arrayListValues().build())));
  }

  /** Adds all the given dimensions. */
  default Dimensions addAll(Collection<DeviceDimension> dimensions) {
    return addAll(
        dimensions.stream()
            .collect(
                toMultimap(
                    DeviceDimension::getName,
                    DeviceDimension::getValue,
                    () -> MultimapBuilder.hashKeys().arrayListValues().build())));
  }

  /** Gets the dimension values of the given dimension name. */
  List<String> get(String name);

  /** Gets the dimension value of the given dimension name. */
  default List<String> get(Dimension.Name name) {
    return get(Ascii.toLowerCase(name.name()));
  }

  /**
   * Gets the first dimension value of the given dimension name, or the default value if there is no
   * dimension with the given name.
   */
  default String getFirst(String name, String defaultValue) {
    return Iterables.getFirst(get(name), defaultValue);
  }

  /**
   * Gets the first dimension value of the given dimension name, or the default value if there is no
   * dimension with the given name.
   */
  default String getFirst(Dimension.Name name, String defaultValue) {
    return getFirst(Ascii.toLowerCase(name.name()), defaultValue);
  }

  /**
   * Gets the only dimension value of the given dimension name.
   *
   * @throws NoSuchElementException if there is no dimension with the given name
   * @throws IllegalArgumentException if there are multiple dimensions with the given name
   */
  default String getOnly(String name) {
    return Iterables.getOnlyElement(get(name));
  }

  /**
   * Gets the only dimension value of the given dimension name, or the default value if there is no
   * dimension with the given name.
   *
   * @throws IllegalArgumentException if there are multiple dimensions with the given name
   */
  default String getOnly(String name, String defaultValue) {
    return Iterables.getOnlyElement(get(name), defaultValue);
  }

  /**
   * Gets the only dimension value of the given dimension name.
   *
   * @throws NoSuchElementException if there is no dimension with the given name
   * @throws IllegalArgumentException if there are multiple dimensions with the given name
   */
  default String getOnly(Dimension.Name name) {
    return getOnly(Ascii.toLowerCase(name.name()));
  }

  /**
   * Gets the only dimension value of the given dimension name, or the default value if there is no
   * dimension with the given name.
   *
   * @throws IllegalArgumentException if there are multiple dimensions with the given name
   */
  default String getOnly(Dimension.Name name, String defaultValue) {
    return getOnly(Ascii.toLowerCase(name.name()), defaultValue);
  }

  /** Gets a copy of all the dimensions. */
  ListMultimap<String, String> getAll();

  /**
   * Replaces all dimensions with the given name with the new values and returns whether the
   * dimensions are changed.
   *
   * <p>For customized dimensions from lab/device config, you can not remove/change the dimension
   * values defined in the config. But you can add more new values to them. When calculating if
   * dimensions are changed, customized dimensions will not be considered either.
   */
  boolean replace(String name, List<String> newValues);

  /**
   * Replaces all dimensions with the given name with the new values and returns whether the
   * dimensions are changed.
   *
   * <p>For customized dimensions from lab/device config, you can not remove/change the dimension
   * values defined in the config. But you can add more new values to them. When calculating if
   * dimensions are changed, customized dimensions will not be considered either.
   */
  default boolean replace(Dimension.Name name, List<String> newValues) {
    return replace(Ascii.toLowerCase(name.name()), newValues);
  }

  /**
   * Removes all existing dimension values with the given name.
   *
   * <p>For customized dimensions from lab/device config, you can not remove the dimension values
   * defined in the config.
   *
   * @param name dimension name
   * @return whether the dimensions are changed
   */
  default boolean remove(String name) {
    return replace(name, ImmutableList.of());
  }

  /**
   * Removes all existing dimension values with the given name.
   *
   * <p>For customized dimensions from lab/device config, you can not remove the dimension values
   * defined in the config.
   *
   * @param name dimension name
   * @return whether the dimensions are changed
   */
  default boolean remove(Dimension.Name name) {
    return remove(Ascii.toLowerCase(name.name()));
  }

  /** Clears all dimensions. */
  default void removeAll() {
    getAll().keySet().forEach(this::remove);
  }

  /** Converts the dimensions to protos. */
  default List<DeviceDimension> toProtos() {
    return getAll().entries().stream()
        .map(
            entry ->
                DeviceDimension.newBuilder()
                    .setName(entry.getKey())
                    .setValue(entry.getValue())
                    .build())
        .collect(toImmutableList());
  }
}
