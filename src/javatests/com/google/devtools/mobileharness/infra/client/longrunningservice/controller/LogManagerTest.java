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

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.truth.extensions.proto.FieldScope;
import com.google.common.truth.extensions.proto.FieldScopes;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogManager.LogRecordsCollector;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogManager.LogRecordsConsumer;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecord.SourceType;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecords;
import com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance;
import com.google.devtools.mobileharness.shared.constant.LogRecordImportance.LogImportanceScope;
import com.google.devtools.mobileharness.shared.context.InvocationContext.ContextScope;
import com.google.devtools.mobileharness.shared.context.InvocationContext.InvocationType;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.logging.MobileHarnessLogFormatter;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.time.Duration;
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

  private static final FieldScope IGNORING_FORMATTED_LOG_RECORD =
      FieldScopes.ignoringFieldDescriptors(
          LogProto.LogRecord.getDescriptor()
              .findFieldByNumber(LogProto.LogRecord.FORMATTED_LOG_RECORD_FIELD_NUMBER));

  private static final Logger rootLogger = Logger.getLogger("");

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
    logManager.getLogHandler().setFormatter(MobileHarnessLogFormatter.getDefaultFormatter());

    rootLogger.addHandler(logManager.getLogHandler());
  }

  @After
  public void tearDown() {
    rootLogger.removeHandler(logManager.getLogHandler());
  }

  @Test
  public void start() {
    logManager.start();
    logManager.addConsumer(logRecordsConsumer);

    try (ContextScope ignored =
        new ContextScope(
            ImmutableMap.of(
                InvocationType.OLC_CLIENT,
                "fake_client_id",
                InvocationType.OMNILAB_TEST,
                "fake_test_id"))) {
      logger.atInfo().log("Foo");
    }

    verify(logRecordsConsumer, timeout(Duration.ofSeconds(2L).toMillis()))
        .consumeLogRecords(
            argThat(
                logRecords -> {
                  try {
                    assertThat(logRecords)
                        .withPartialScope(IGNORING_FORMATTED_LOG_RECORD)
                        .isEqualTo(
                            LogRecords.newBuilder()
                                .addLogRecord(
                                    LogProto.LogRecord.newBuilder()
                                        .setSourceType(SourceType.SELF)
                                        .setClientId("fake_client_id")
                                        .setImportance(Importance.NORMAL.value()))
                                .build());
                    return logRecords
                        .getLogRecord(0)
                        .getFormattedLogRecord()
                        .contains("Foo {test_id=fake_test_id, olc_client_id=fake_client_id}");
                  } catch (RuntimeException | Error e) {
                    return false;
                  }
                }));

    try (LogImportanceScope ignored =
        new LogImportanceScope(Importance.IMPORTANT, record -> true)) {
      logger.atInfo().log("Goo");
    }

    verify(logRecordsConsumer, timeout(Duration.ofSeconds(2L).toMillis()))
        .consumeLogRecords(
            argThat(
                logRecords -> {
                  try {
                    assertThat(logRecords)
                        .withPartialScope(IGNORING_FORMATTED_LOG_RECORD)
                        .isEqualTo(
                            LogRecords.newBuilder()
                                .addLogRecord(
                                    LogProto.LogRecord.newBuilder()
                                        .setSourceType(SourceType.SELF)
                                        .setImportance(Importance.IMPORTANT.value()))
                                .build());
                    return logRecords.getLogRecord(0).getFormattedLogRecord().contains("Goo");
                  } catch (RuntimeException | Error e) {
                    return false;
                  }
                }));
  }
}
