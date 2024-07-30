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

import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecord.SourceType;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecords;
import com.google.devtools.mobileharness.shared.constant.LogRecordImportance;
import com.google.devtools.mobileharness.shared.constant.LogRecordImportance.LogImportanceScope;
import com.google.devtools.mobileharness.shared.context.InvocationContext;
import com.google.devtools.mobileharness.shared.context.InvocationContext.InvocationInfo;
import com.google.devtools.mobileharness.shared.context.InvocationContext.InvocationType;
import com.google.devtools.mobileharness.shared.util.command.linecallback.CommandOutputLogger;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Log manager for managing logs of the server.
 *
 * <p>The efficiency of {@link LogRecordsCollector} and {@link LogRecordsConsumer}s will <b>not</b>
 * affect the throughput of {@link #getLogHandler()}. Their long execution time (if so) will only
 * cause old log records are discarded.
 *
 * <p>This class is thread safe.
 *
 * @param <D> output type of downstream collector of log records
 */
@Singleton
public class LogManager<D> {

  /**
   * Downstream collector of log records, whose output will be consumed by multiple {@link
   * LogRecordsConsumer}s.
   */
  public interface LogRecordsCollector<D> {

    D collectLogRecords(LogRecords.Builder logRecords);
  }

  /** Consumer for consuming log records. */
  public interface LogRecordsConsumer<D> {

    void consumeLogRecords(D logRecords);
  }

  private static final int CAPACITY = 10;

  private final Handler logHandler;

  private final ListeningExecutorService threadPool;

  private final LogRecordsCollector<D> downstreamCollector;

  @GuardedBy("itself")
  private final Set<LogRecordsConsumer<D>> consumers = new HashSet<>();

  /**
   * A ring buffer of log records. When it is full and a new record is being added, the earliest
   * record will be discarded.
   *
   * <p>The record class type is {@link LogRecord} / {@link LogProto.LogRecord} / {@link
   * LogRecordWithContext}.
   */
  @GuardedBy("itself")
  private final Object[] records = new Object[CAPACITY];

  /** Exclusive. Value range: [0, records.length - 1]. */
  @GuardedBy("records")
  private int recordsEndIndex = 0;

  /** Value range: [0, records.length]. */
  @GuardedBy("records")
  private int recordsSize = 0;

  @Inject
  LogManager(ListeningExecutorService threadPool, LogRecordsCollector<D> downstreamCollector) {
    this.threadPool = threadPool;
    this.downstreamCollector = downstreamCollector;
    this.logHandler = new LogHandler();
  }

  /** The handler will not handle logs from {@link CommandOutputLogger}. */
  public Handler getLogHandler() {
    return logHandler;
  }

  public void addConsumer(LogRecordsConsumer<D> consumer) {
    synchronized (consumers) {
      consumers.add(consumer);
      consumers.notifyAll();
    }
  }

  public void removeConsumer(LogRecordsConsumer<D> consumer) {
    synchronized (consumers) {
      consumers.remove(consumer);
    }
  }

  /** Starts to consume log records asynchronously. */
  public void start() {
    logFailure(
        threadPool.submit(threadRenaming(this::run, () -> "log-manager")),
        Level.SEVERE,
        "Fatal error in log manager");
  }

  private Void run() throws InterruptedException {
    while (!Thread.interrupted()) {
      // Waits until consumers is not empty.
      synchronized (consumers) {
        while (consumers.isEmpty()) {
          consumers.wait();
        }
      }
      LogRecords.Builder logRecords = LogRecords.newBuilder();
      synchronized (records) {
        // Waits until records is not empty.
        while (recordsSize == 0) {
          records.wait();
        }

        // Gets start index of records.
        int index = recordsEndIndex - recordsSize;
        if (index < 0) {
          index += records.length;
        }

        // Polls all records.
        do {
          Object logRecord = records[index];
          records[index] = null;
          logRecords.addLogRecord(generateLogRecord(logRecord));
          index++;
          if (index == records.length) {
            index = 0;
          }
          recordsSize--;
        } while (recordsSize > 0);
        recordsEndIndex = 0;
      }

      // Consumes log records.
      // Notes that consumers may become empty which will discard log records, however, it is
      // acceptable, compared with #1: potential deadlock on consumers or #2: the case that old
      // records appear in result but new ones are discarded.
      D output = downstreamCollector.collectLogRecords(logRecords);
      synchronized (consumers) {
        consumers.forEach(consumer -> consumer.consumeLogRecords(output));
      }
    }
    return null;
  }

  void addExternalLogRecord(LogProto.LogRecord logRecord) {
    addLogRecordToBuffer(logRecord);
  }

  /**
   * Adds a log record to the ring buffer. Discards the earliest one if the buffer is full.
   *
   * <p>The record class type is {@link LogRecord} / {@link LogProto.LogRecord} / {@link
   * LogRecordWithContext}.
   */
  private void addLogRecordToBuffer(Object logRecord) {
    synchronized (records) {
      records[recordsEndIndex] = logRecord;
      recordsEndIndex++;
      if (recordsEndIndex == records.length) {
        recordsEndIndex = 0;
      }
      if (recordsSize < records.length) {
        recordsSize++;
      }
      records.notifyAll();
    }
  }

  private class LogHandler extends Handler {

    private LogHandler() {
      setFilter(
          logRecord ->
              !Objects.equals(logRecord.getSourceClassName(), CommandOutputLogger.class.getName()));
    }

    @Override
    public void publish(LogRecord logRecord) {
      if (isLoggable(logRecord)) {
        LogImportanceScope importanceScope = LogImportanceScope.getCurrentScope();

        Map<InvocationType, InvocationInfo> context = InvocationContext.getCurrentContext();
        InvocationInfo clientIdInvocationInfo = context.get(InvocationType.OLC_CLIENT);
        String clientId = clientIdInvocationInfo == null ? null : clientIdInvocationInfo.id();
        String formattedLogRecord = context.isEmpty() ? null : getFormatter().format(logRecord);

        if (importanceScope == null && clientId == null) {
          addLogRecordToBuffer(logRecord);
        } else {
          addLogRecordToBuffer(
              new LogRecordWithContext(logRecord, importanceScope, clientId, formattedLogRecord));
        }
      }
    }

    @Override
    public void flush() {
      // Does nothing.
    }

    @Override
    public void close() {
      // Does nothing.
    }
  }

  private LogProto.LogRecord generateLogRecord(Object logRecord) {
    if (logRecord instanceof LogProto.LogRecord) {
      return (LogProto.LogRecord) logRecord;
    }

    LogRecord record;
    LogImportanceScope importanceScope;
    String clientId;
    String formattedLogRecord;
    if (logRecord instanceof LogRecord) {
      record = (LogRecord) logRecord;
      importanceScope = null;
      clientId = null;
      formattedLogRecord = null;
    } else {
      LogRecordWithContext recordWithContext = (LogRecordWithContext) logRecord;
      record = recordWithContext.logRecord;
      importanceScope = recordWithContext.logImportanceScope;
      clientId = recordWithContext.clientId;
      formattedLogRecord = recordWithContext.formattedLogRecord;
    }

    if (formattedLogRecord == null) {
      formattedLogRecord = logHandler.getFormatter().format(record);
    }
    int importance = LogRecordImportance.getLogRecordImportance(record, importanceScope).value();
    LogProto.LogRecord.Builder result =
        LogProto.LogRecord.newBuilder()
            .setFormattedLogRecord(formattedLogRecord)
            .setSourceType(SourceType.SELF)
            .setImportance(importance);
    if (clientId != null) {
      result.setClientId(clientId);
    }
    return result.build();
  }

  private static class LogRecordWithContext {

    private final LogRecord logRecord;

    @Nullable private final LogImportanceScope logImportanceScope;

    @Nullable private final String clientId;

    @Nullable private final String formattedLogRecord;

    private LogRecordWithContext(
        LogRecord logRecord,
        @Nullable LogImportanceScope logImportanceScope,
        @Nullable String clientId,
        @Nullable String formattedLogRecord) {
      this.logRecord = logRecord;
      this.logImportanceScope = logImportanceScope;
      this.clientId = clientId;
      this.formattedLogRecord = formattedLogRecord;
    }
  }
}
