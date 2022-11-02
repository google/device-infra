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

package com.google.wireless.qa.mobileharness.shared.controller.event.util;

import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scope;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link Scope} to be used for injecting MH event variables only while those events are active.
 */
public class EventInjectionScope implements Scope {

  public static final EventInjectionScope instance = new EventInjectionScope();

  private final ThreadLocal<Map<Key<?>, Object>> localMapping = new ThreadLocal<>();
  private final ThreadLocal<Boolean> localIsInScope = new ThreadLocal<>();

  EventInjectionScope() {}

  /** Sets the event scope as active on the local thread. */
  public void enter() {
    localIsInScope.set(true);
  }

  /** Sets the event scope as un-active on the current thread and clears the event lookup. */
  public void exit() {
    localIsInScope.set(false);
    localMapping.set(null);
  }

  /**
   * Puts a value binding in the event scope lookup for the current thread.
   *
   * @param clazz the class to bind the value to
   * @param value the value to bind
   */
  public <T> void put(Class<T> clazz, T value) {
    Key<T> key = Key.get(clazz);
    put(key, value);
  }

  /**
   * Puts a value binding in the event scope lookup of the current thread.
   *
   * @param key the key to bind the value to
   * @param value the value to bind
   */
  public <T> void put(Key<T> key, T value) {
    getLocalMap().put(key, value);
  }

  @Override
  public <T> Provider<T> scope(Key<T> key, Provider<T> unscoped) {
    return () -> {
      if (localIsInScope.get() != null && localIsInScope.get()) {
        Map<Key<?>, Object> localMap = getLocalMap();
        if (localMap.containsKey(key)) {
          @SuppressWarnings("unchecked")
          T value = (T) localMap.get(key);
          return value;
        }
      }

      T value = unscoped.get();
      put(key, value);
      return value;
    };
  }

  private Map<Key<?>, Object> getLocalMap() {
    Map<Key<?>, Object> localMap = localMapping.get();
    if (localMap == null) {
      localMap = new HashMap<>();
      localMapping.set(localMap);
    }

    return localMap;
  }
}
