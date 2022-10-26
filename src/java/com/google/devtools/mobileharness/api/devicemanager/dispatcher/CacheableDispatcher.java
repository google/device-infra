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

package com.google.devtools.mobileharness.api.devicemanager.dispatcher;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResults;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResult.DispatchType;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResults;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCacheManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/** Device dispatcher for dispatching active devices, including cached devices. */
public abstract class CacheableDispatcher implements Dispatcher {
  /** Default Cache device type suffix to remove. */
  private static final String DEFAULT_CACHE_DEVICE_SUFFIX = "Dispatcher";

  /**
   * Dispatches devices, including cached devices.
   *
   * <p>It returns the result of: {@link #dispatchLiveDevices} + {@link #getCachedDevices}
   */
  @Override
  public Map<String, DispatchType> dispatchDevices(
      DetectionResults detectionResults, DispatchResults dispatchResults)
      throws MobileHarnessException, InterruptedException {
    Map<String, DispatchType> dispatchIds =
        new HashMap<>(dispatchLiveDevices(detectionResults, dispatchResults));
    Set<String> cachedIds =
        getCachedDevices(
            dispatchIds.entrySet().stream()
                .filter(entrySet -> entrySet.getValue().equals(DispatchType.LIVE))
                .map(Entry::getKey)
                .collect(toImmutableSet()),
            detectionResults,
            dispatchResults);
    // Overrides the result when the device is filtered or not dispatched.
    // Type prioirty order: LIVE > SUB_DEVICE > CACHE > ONLY_FILTER
    // Example:
    // live results: id0(live), id1(only_filter), id2(sub_device)
    // cache results: id0(cache), id1(cache), id2(cache)
    // merge results: id0(live), id1(cache), id2(sub_device)
    cachedIds.forEach(
        id ->
            dispatchIds.compute(
                id,
                (k, v) ->
                    (v == null || v.equals(DispatchType.ONLY_FILTER)) ? DispatchType.CACHE : v));
    return dispatchIds;
  }

  /**
   * Gets cached devices.
   *
   * <p>This is the generic call which is intended to be overridden. Sub-class should implement this
   * method to get cached devices based on one (or more) device type(s) that it is interested in.
   */
  protected Set<String> getCachedDevices(
      Set<String> liveControlIds,
      DetectionResults detectionResults,
      DispatchResults dispatchResults)
      throws MobileHarnessException {
    String dispatchClassName = getClass().getSimpleName();
    return DeviceCacheManager.getInstance()
        .getCachedDevices(
            dispatchClassName.substring(
                0, dispatchClassName.length() - DEFAULT_CACHE_DEVICE_SUFFIX.length()));
  }

  /**
   * Dispatches live devices. Subclasses should provide this method to return the set of devices
   * which they can actually detect without caring about the cache.
   *
   * @throws MobileHarnessException if fails to dispatch devices
   */
  abstract Map<String, DispatchType> dispatchLiveDevices(
      DetectionResults detectionResults, DispatchResults dispatchResults)
      throws MobileHarnessException, InterruptedException;
}
