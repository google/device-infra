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

package com.google.devtools.mobileharness.api.devicemanager.detector.model;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.LinkedListMultimap;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult.DetectionType;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** A set that combines all detection results. */
public class DetectionResults implements Cloneable {
  /** All the {@link DetectionResult}s group by device control id. */
  private final LinkedListMultimap<String, DetectionResult> results = LinkedListMultimap.create();

  public DetectionResults() {}

  public DetectionResults(DetectionResults other) {
    this(other.getAll().values());
  }

  @VisibleForTesting
  public DetectionResults(Collection<DetectionResult> resultList) {
    resultList.forEach(this::add);
  }

  @Override
  public Object clone() {
    return new DetectionResults(this);
  }

  /** Adds the given {@link DetectionResult} to results. */
  public synchronized void add(DetectionResult detectionResult) {
    results.put(detectionResult.deviceControlId(), detectionResult);
  }

  /** Adds the given list of {@link DetectionResult}s to results. */
  public synchronized void add(Collection<DetectionResult> resultList) {
    resultList.forEach(this::add);
  }

  /** Removes all results of the given device control id from results. */
  public synchronized void remove(String deviceControlId) {
    results.removeAll(deviceControlId);
  }

  /** Returns all {@link DetectionResult}s of the results. */
  public synchronized LinkedListMultimap<String, DetectionResult> getAll() {
    return LinkedListMultimap.create(results);
  }

  /** Returns all {@link DetectionResult}s with the specified detection type. */
  public synchronized Collection<DetectionResult> getByType(DetectionType detectionType) {
    return results.values().stream()
        .filter(detectorResult -> detectorResult.detectionType().equals(detectionType))
        .collect(Collectors.toList());
  }

  /** Returns all the {@link DetectionResult}s with the specified detection type and detail. */
  public synchronized Collection<DetectionResult> getByTypeAndDetail(
      DetectionType detectionType, @Nullable Object detail) {
    return results.values().stream()
        .filter(
            detectorResult ->
                detectorResult.detectionType().equals(detectionType)
                    && detectorResult.detectionDetail().equals(Optional.ofNullable(detail)))
        .collect(Collectors.toList());
  }
}
