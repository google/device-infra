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

package com.google.devtools.mobileharness.api.model.lab.out;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Local implementation of {@link Properties}.
 *
 * <p>Do <b>not</b> make it public.
 */
@ThreadSafe
class LocalProperties implements Properties {

  private final Map<String, String> properties = new ConcurrentHashMap<>();

  @Override
  public Optional<String> put(String key, String value) {
    return Optional.ofNullable(properties.put(checkNotNull(key), checkNotNull(value)));
  }

  @Override
  public Optional<String> get(String key) {
    return Optional.ofNullable(properties.get(key));
  }

  @Override
  public Optional<String> remove(String key) {
    return Optional.ofNullable(properties.remove(key));
  }

  @Override
  public Map<String, String> getAll() {
    return ImmutableMap.copyOf(properties);
  }

  @Override
  public void clear() {
    properties.clear();
  }
}
