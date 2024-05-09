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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.logging.v2.LogEntry;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link LogEntryQueueImpl}. */
@RunWith(JUnit4.class)
public class LogEntryQueueImplTest {

  private LogEntryQueue logEntryQueue;

  @Before
  public void setUp() throws Exception {
    logEntryQueue = new LogEntryQueueImpl();
  }

  @Test
  public void add_success() {
    assertThat(logEntryQueue.add(LogEntry.getDefaultInstance())).isTrue();

    assertThat(logEntryQueue.poll(LogEntryQueueImpl.MAX_BACKLOG)).hasSize(1);
  }

  @Test
  public void add_overSize() {
    for (int i = 0; i < LogEntryQueueImpl.MAX_BACKLOG; i++) {
      assertThat(logEntryQueue.add(LogEntry.getDefaultInstance())).isTrue();
    }
    assertThat(logEntryQueue.add(LogEntry.getDefaultInstance())).isFalse();

    assertThat(logEntryQueue.poll(LogEntryQueueImpl.MAX_BACKLOG * 2))
        .hasSize(LogEntryQueueImpl.MAX_BACKLOG);
  }

  @Test
  public void addAll_success() {
    assertThat(logEntryQueue.addAll(ImmutableList.of(LogEntry.getDefaultInstance()))).isTrue();

    assertThat(logEntryQueue.poll(LogEntryQueueImpl.MAX_BACKLOG)).hasSize(1);
  }

  @Test
  public void addAll_overSize() {
    List<LogEntry> logEntries =
        Collections.nCopies(LogEntryQueueImpl.MAX_BACKLOG + 1, LogEntry.getDefaultInstance());
    assertThat(logEntryQueue.addAll(logEntries)).isFalse();

    assertThat(logEntryQueue.poll(LogEntryQueueImpl.MAX_BACKLOG * 2))
        .hasSize(LogEntryQueueImpl.MAX_BACKLOG);
  }
}
