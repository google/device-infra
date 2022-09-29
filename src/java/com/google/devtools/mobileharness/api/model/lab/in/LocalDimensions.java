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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.List;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Local implementation of {@link Dimensions}.
 *
 * <p>Do <b>not</b> make it public.
 */
@ThreadSafe
public class LocalDimensions implements Dimensions {

  @GuardedBy("itself")
  private final ListMultimap<String, String> dimensions =
      MultimapBuilder.hashKeys().arrayListValues().build();

  @CanIgnoreReturnValue
  @Override
  public Dimensions add(String name, String value) {
    synchronized (dimensions) {
      dimensions.put(name, value);
    }
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public Dimensions addAll(Multimap<String, String> dimensions) {
    synchronized (this.dimensions) {
      this.dimensions.putAll(dimensions);
    }
    return this;
  }

  @Override
  public List<String> get(String name) {
    synchronized (dimensions) {
      return ImmutableList.copyOf(dimensions.get(name));
    }
  }

  @Override
  public ListMultimap<String, String> getAll() {
    synchronized (dimensions) {
      return MultimapBuilder.hashKeys().arrayListValues().build(dimensions);
    }
  }

  @Override
  public boolean replace(String name, List<String> newValues) {
    synchronized (dimensions) {
      return !dimensions.replaceValues(name, newValues).equals(newValues);
    }
  }
}
