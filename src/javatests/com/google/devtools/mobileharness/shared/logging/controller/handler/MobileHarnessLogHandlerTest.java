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

package com.google.devtools.mobileharness.shared.logging.controller.handler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.shared.logging.controller.queue.LogEntryQueue;
import com.google.devtools.mobileharness.shared.logging.util.LogEntryUtil;
import com.google.logging.v2.LogEntry;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link MobileHarnessLogHandler}. */
@RunWith(JUnit4.class)
public class MobileHarnessLogHandlerTest {
  private static final LogRecord LOG_RECORD = new LogRecord(Level.INFO, "");
  private static final LogEntry LOG_ENTRY = LogEntry.getDefaultInstance();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private LogEntryUtil logEntryUtil;
  @Mock private LogEntryQueue logEntryQueue;

  private MobileHarnessLogHandler mobileHarnessLogHandler;

  @Before
  public void setUp() throws Exception {
    mobileHarnessLogHandler = new MobileHarnessLogHandler(logEntryQueue, logEntryUtil);
  }

  @Test
  public void publish_success() {
    when(logEntryUtil.generateLogEntry(LOG_RECORD)).thenReturn(LOG_ENTRY);

    mobileHarnessLogHandler.publish(LOG_RECORD);

    verify(logEntryQueue).add(LOG_ENTRY);
  }
}
