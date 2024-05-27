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

package com.google.devtools.mobileharness.shared.util.base;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * A set that supports getting the last added element and removing from the given element to the
 * last element by the adding order.
 *
 * @param <E> the type of elements
 * @implSpec all operations have O(1) time complexity ({@link #removeUntilLast} has O(k) time
 *     complexity where k is the number of elements to remove)
 */
@NotThreadSafe
public class StackSet<E> {

  private final List<E> elements = new ArrayList<>();
  private final Map<E, Integer> indexes = new HashMap<>();

  @Nullable private volatile E lastElement;

  /**
   * Adds an element.
   *
   * @throws NullPointerException if the element is {@code null}
   * @throws IllegalStateException if the {@linkplain Object#equals(Object) same} element is in the
   *     set
   */
  public void add(E e) {
    checkNotNull(e);
    checkState(!indexes.containsKey(e));
    indexes.put(e, elements.size());
    elements.add(e);
    lastElement = e;
  }

  /** Gets the last added element, or empty if the set is empty. */
  public Optional<E> getLast() {
    return Optional.ofNullable(lastElement);
  }

  /**
   * Removes from the given element to the last element by the adding order.
   *
   * <p>Returns immediately if the element is not in the set.
   */
  public void removeUntilLast(E e) {
    if (!indexes.containsKey(e)) {
      return;
    }
    int index = indexes.get(e);
    for (int i = elements.size() - 1; i >= index; i--) {
      indexes.remove(elements.remove(i));
    }
    lastElement = index == 0 ? null : elements.get(index - 1);
  }
}
