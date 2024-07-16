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

package com.google.devtools.mobileharness.infra.client.longrunningservice.controller;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogManager.LogRecordsCollector;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogManager.LogRecordsConsumer;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecord.SourceType;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecords;
import com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance;
import com.google.devtools.mobileharness.shared.constant.LogRecordImportance.LogImportanceScope;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.time.Duration;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class LogManagerTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private static final Logger ROOT_LOGGER = Logger.getLogger("");

  @Mock private LogRecordsConsumer<LogRecords> logRecordsConsumer;

  @Bind
  private final ListeningExecutorService threadPool =
      ThreadPools.createStandardThreadPool("testing-thread-pool");

  @Bind
  private final LogRecordsCollector<LogRecords> downstreamCollector = LogRecords.Builder::build;

  @Inject private LogManager<LogRecords> logManager;

  @Before
  public void setUp() throws Exception {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    logManager.getLogHandler().setFormatter(new SimpleLogFormatter());

    ROOT_LOGGER.addHandler(logManager.getLogHandler());
  }

  @After
  public void tearDown() {
    ROOT_LOGGER.removeHandler(logManager.getLogHandler());
  }

  @Test
  public void start() {
    logManager.start();
    logManager.addConsumer(logRecordsConsumer);

    logger.atInfo().log("Foo");

    verify(logRecordsConsumer, timeout(Duration.ofSeconds(2L).toMillis()))
        .consumeLogRecords(
            LogRecords.newBuilder()
                .addLogRecord(
                    LogProto.LogRecord.newBuilder()
                        .setFormattedLogRecord("Foo")
                        .setSourceType(SourceType.SELF)
                        .setImportance(Importance.NORMAL.value()))
                .build());

    try (LogImportanceScope ignored =
        new LogImportanceScope(Importance.IMPORTANT, record -> true)) {
      logger.atInfo().log("Goo");
    }

    verify(logRecordsConsumer, timeout(Duration.ofSeconds(2L).toMillis()))
        .consumeLogRecords(
            LogRecords.newBuilder()
                .addLogRecord(
                    LogProto.LogRecord.newBuilder()
                        .setFormattedLogRecord("Goo")
                        .setSourceType(SourceType.SELF)
                        .setImportance(Importance.IMPORTANT.value()))
                .build());
  }

  private static class SimpleLogFormatter extends Formatter {

    @Override
    public String format(LogRecord record) {
      return record.getMessage();
    }
  }
}
