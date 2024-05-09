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

package com.google.devtools.mobileharness.shared.logging.controller.queue;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.logging.v2.LogEntry;
import java.util.List;

/** The queue interface for storage the {@link LogEntry} in memory. */
public interface LogEntryQueue {
  /**
   * Adds a {@link LogEntry} to the queue.
   *
   * @param logEntry the element to add
   * @return {@code true} if the element was added to this queue, else {@code false}
   */
  @CanIgnoreReturnValue
  boolean add(LogEntry logEntry);

  /**
   * Adds {@link LogEntry}s to the queue.
   *
   * @param logEntries the elements to add
   * @return {@code true} if the element was added to this queue, else {@code false}
   */
  boolean addAll(List<LogEntry> logEntries);

  /**
   * Polls the {@link LogEntry}s from the queue.
   *
   * @param maxElements the maximum number of elements to transfer
   * @return elements transferred
   */
  List<LogEntry> poll(int maxElements);
}
