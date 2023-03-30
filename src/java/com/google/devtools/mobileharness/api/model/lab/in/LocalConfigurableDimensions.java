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

import static com.google.common.collect.Multimaps.toMultimap;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.proto.Common.StrPair;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * {@link Dimensions} which gets data from {@link LocalDimensions} and {@link ApiConfig}.
 *
 * <p>Do <b>not</b> make it public.
 */
@ThreadSafe
class LocalConfigurableDimensions implements Dimensions {

  @GuardedBy("itself")
  private final Dimensions dimensions = new LocalDimensions();

  @GuardedBy("dimensions")
  @Nullable
  private final ApiConfig apiConfig;

  @GuardedBy("dimensions")
  @Nullable
  private final LocalDimensions otherSourceLocalDimensions;

  private final String deviceId;
  private final boolean required;

  LocalConfigurableDimensions(
      @Nullable ApiConfig apiConfig,
      @Nullable LocalDimensions otherSourceLocalDimensions,
      String deviceId,
      boolean required) {
    this.apiConfig = apiConfig;
    this.otherSourceLocalDimensions = otherSourceLocalDimensions;
    this.deviceId = deviceId;
    this.required = required;
  }

  @CanIgnoreReturnValue
  @Override
  public Dimensions add(String name, String value) {
    synchronized (dimensions) {
      dimensions.add(name, value);
    }
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public Dimensions addAll(Multimap<String, String> dimensions) {
    synchronized (this.dimensions) {
      this.dimensions.addAll(dimensions);
    }
    return this;
  }

  @Override
  public List<String> get(String name) {
    synchronized (dimensions) {
      return Stream.<Stream<String>>of(
              (apiConfig == null
                      ? Stream.<StrPair>empty()
                      : (required
                              ? apiConfig.getRequiredDimensions(deviceId)
                              : apiConfig.getSupportedDimensions(deviceId))
                          .stream())
                  .filter(pair -> pair.getName().equals(name))
                  .map(StrPair::getValue),
              dimensions.get(name).stream(),
              otherSourceLocalDimensions == null
                  ? Stream.empty()
                  : otherSourceLocalDimensions.get(name).stream())
          .reduce(Stream::concat)
          .orElseGet(Stream::empty)
          .distinct()
          .collect(Collectors.toList());
    }
  }

  @Override
  public ListMultimap<String, String> getAll() {
    synchronized (dimensions) {
      SetMultimap<String, String> result =
          (apiConfig == null
                  ? Stream.<StrPair>empty()
                  : (required
                          ? apiConfig.getRequiredDimensions(deviceId)
                          : apiConfig.getSupportedDimensions(deviceId))
                      .stream())
              .collect(
                  toMultimap(
                      StrPair::getName,
                      StrPair::getValue,
                      () -> MultimapBuilder.linkedHashKeys().linkedHashSetValues().build()));
      result.putAll(dimensions.getAll());
      if (otherSourceLocalDimensions != null) {
        result.putAll(otherSourceLocalDimensions.getAll());
      }
      // Use a LinkedHashMultimap to dedup the same <key, value> entries and then convert to a
      // ListMultimap.
      return result.entries().stream()
          .collect(
              toMultimap(
                  Entry::getKey,
                  Entry::getValue,
                  () -> MultimapBuilder.hashKeys().arrayListValues().build()));
    }
  }

  @Override
  public boolean replace(String name, List<String> newValues) {
    synchronized (dimensions) {
      return dimensions.replace(name, newValues);
    }
  }
}
