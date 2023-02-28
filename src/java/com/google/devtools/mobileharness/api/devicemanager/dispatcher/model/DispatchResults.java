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

package com.google.devtools.mobileharness.api.devicemanager.dispatcher.model;

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResult.DispatchType;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** A set that combine all {@link DispatchResult}s. */
public class DispatchResults {
  /** All the {@link DispatchResult}s group by device control id. */
  private final HashMap<String, DispatchResult> results = new HashMap<>();

  public DispatchResults() {}

  @VisibleForTesting
  public DispatchResults(Collection<DispatchResult> resultList) {
    resultList.forEach(this::addIfNotExist);
  }

  /** Adds the given {@link DispatchResult} to results if the device control id is not exist. */
  public synchronized boolean addIfNotExist(DispatchResult dispatchResult) {
    if (!results.containsKey(dispatchResult.deviceId().controlId())) {
      results.put(dispatchResult.deviceId().controlId(), dispatchResult);
      return true;
    }
    return false;
  }

  /** Upserts the given {@link DispatchResult} to results. */
  public synchronized void forceUpsert(DispatchResult dispatchResult) {
    results.put(dispatchResult.deviceId().controlId(), dispatchResult);
  }

  /** Updates the {@link DispatchType} of the result by device control id. */
  public synchronized boolean forceUpdateDispatchType(
      String deviceControlId, DispatchType dispatchType) {
    if (results.containsKey(deviceControlId)) {
      DispatchResult oldValue = results.get(deviceControlId);
      results.replace(
          deviceControlId,
          DispatchResult.of(oldValue.deviceId(), dispatchType, oldValue.deviceType()));
      return true;
    }
    return false;
  }

  /** Removes the {@link DispatchResult} of the given device control id from results. */
  public synchronized void remove(String deviceControlId) {
    results.remove(deviceControlId);
  }

  /** Gets the {@link DispatchResult} by device control id. */
  @Nullable
  public synchronized DispatchResult get(String deviceControlId) {
    return results.get(deviceControlId);
  }

  /** Returns all {@link DispatchResult}s of the results. */
  public synchronized Collection<DispatchResult> getAll() {
    return results.values();
  }

  /** Returns all device control ids of the results. */
  public synchronized Set<String> getAllDeviceControlIds() {
    return results.keySet();
  }

  /**
   * Returns all device control ids which dispatch type are in {@code dispatchTypes} of the results
   */
  public synchronized Set<String> getDeviceControlIds(DispatchType... dispatchTypes) {
    Set<DispatchType> types = new HashSet<>(Arrays.asList(dispatchTypes));
    return results.values().stream()
        .filter(dispatchResult -> types.contains(dispatchResult.dispatchType()))
        .map(result -> result.deviceId().controlId())
        .collect(Collectors.toSet());
  }

  /** Merges two {@link DispatchResults}. */
  public synchronized void mergeFrom(DispatchResults others) {
    // TODO: temporal solution, we should use the topological order to handle it.
    others
        .getAll()
        .forEach(
            dispatchResult -> {
              if (!results.containsKey(dispatchResult.deviceId().controlId())
                  || dispatchResult.dispatchType().equals(DispatchType.SUB_DEVICE)
                  || results
                      .get(dispatchResult.deviceId().controlId())
                      .dispatchType()
                      .equals(DispatchType.ONLY_FILTER)) {
                results.put(dispatchResult.deviceId().controlId(), dispatchResult);
              }
            });
  }
}
