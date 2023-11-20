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

package com.google.devtools.mobileharness.shared.commandhistory.controller;

import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toProtoTimestamp;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ticker;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.mobileharness.shared.commandhistory.proto.CommandRecordProto.LocalCommandRecord;
import com.google.devtools.mobileharness.shared.commandhistory.proto.CommandRecordProto.LocalCommandRecord.CommandEndedEvent;
import com.google.devtools.mobileharness.shared.commandhistory.proto.CommandRecordProto.LocalCommandRecord.CommandStartedEvent;
import com.google.devtools.mobileharness.shared.commandhistory.proto.CommandRecordProto.LocalCommandRecord.CommandStartedEvent.CommandStartFailure;
import com.google.devtools.mobileharness.shared.commandhistory.proto.CommandRecordProto.LocalCommandRecord.CommandStartedEvent.CommandStartSuccess;
import com.google.devtools.mobileharness.shared.commandhistory.proto.CommandRecordProto.LocalCommandRecord.CommandStartedEvent.InvocationInfo;
import com.google.devtools.mobileharness.shared.subprocess.listener.ProcessBuilderListener;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadFactoryUtil;
import com.google.protobuf.util.Durations;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import javax.annotation.Nullable;

/**
 * Recorder for monitoring subprocesses of the current process, generating corresponding {@link
 * LocalCommandRecord}s and sending them to {@link LocalCommandHistoryManager}.
 */
public class LocalCommandHistoryRecorder implements ProcessBuilderListener.Handler {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final LocalCommandHistoryRecorder INSTANCE = new LocalCommandHistoryRecorder();

  public static LocalCommandHistoryRecorder getInstance() {
    return INSTANCE;
  }

  private final LocalCommandHistoryManager manager;
  private final ListeningExecutorService threadPool;
  private final Ticker ticker;
  private final Clock clock;

  private final AtomicLong nextCommandSequenceNumber = new AtomicLong(0L);

  private LocalCommandHistoryRecorder() {
    this(
        LocalCommandHistoryManager.getInstance(),
        MoreExecutors.listeningDecorator(
            Executors.newCachedThreadPool(
                ThreadFactoryUtil.createThreadFactory("subprocess-listener", /* daemon= */ true))),
        Ticker.systemTicker(),
        Clock.systemUTC());
  }

  @VisibleForTesting
  LocalCommandHistoryRecorder(
      LocalCommandHistoryManager manager,
      ListeningExecutorService threadPool,
      Ticker ticker,
      Clock clock) {
    this.manager = manager;
    this.threadPool = threadPool;
    this.ticker = ticker;
    this.clock = clock;
  }

  @Override
  public void onProcessStarted(
      List<String> command,
      @Nullable Process process,
      @Nullable Throwable exception,
      List<StackTraceElement> stackTrace) {
    try {
      long commandSequenceNumber = nextCommandSequenceNumber.getAndIncrement();
      LocalCommandRecord.Builder record = createCommandRecordBuilder(commandSequenceNumber);

      record.setCommandStartedEvent(
          createCommandStartedEvent(command, process, exception, stackTrace));

      manager.add(record.build());

      if (process != null) {
        addProcessEndedListener(process, commandSequenceNumber);
      }
    } catch (RuntimeException | Error e) {
      logger.atWarning().withCause(e).log("Failed to record command history, command=%s", command);
    }
  }

  /** Records the starting time of the current process. */
  public void start() {
    manager.start(Duration.ofNanos(ticker.read()), clock.instant());
  }

  private void addProcessEndedListener(Process process, long commandSequenceNumber) {
    logFailure(
        threadPool.submit(() -> waitAndHandleProcessEnded(process, commandSequenceNumber)),
        Level.WARNING,
        "Exception thrown while waiting or handling process ended,"
            + " process=%s, command_sequence_number=%s",
        process,
        commandSequenceNumber);
  }

  /** Synchronously waits a subprocess to exit and then adds corresponding record. */
  private Void waitAndHandleProcessEnded(Process process, long commandSequenceNumber)
      throws InterruptedException {
    int processExitCode = process.waitFor();

    LocalCommandRecord.Builder record = createCommandRecordBuilder(commandSequenceNumber);
    record.setCommandEndedEvent(CommandEndedEvent.newBuilder().setExitCode(processExitCode));
    manager.add(record.build());
    return null;
  }

  private LocalCommandRecord.Builder createCommandRecordBuilder(long commandSequenceNumber) {
    return LocalCommandRecord.newBuilder()
        .setLocalCommandSequenceNumber(commandSequenceNumber)
        .setLocalElapsedTime(Durations.fromNanos(ticker.read()))
        .setLocalTimestamp(toProtoTimestamp(clock.instant()));
  }

  private static CommandStartedEvent createCommandStartedEvent(
      List<String> command,
      @Nullable Process process,
      @Nullable Throwable exception,
      List<StackTraceElement> stackTrace) {
    Thread currentThread = Thread.currentThread();
    CommandStartedEvent.Builder result =
        CommandStartedEvent.newBuilder()
            .addAllCommand(command)
            .setInvocationInfo(
                InvocationInfo.newBuilder()
                    .setThreadId(currentThread.getId())
                    .setThreadName(currentThread.getName())
                    .setStackTrace(ErrorModelConverter.toStackTraceProto(stackTrace.stream())));

    if (exception == null) {
      CommandStartSuccess.Builder commandStartSuccess = CommandStartSuccess.newBuilder();
      if (process != null) {
        long pid = process.pid();
        commandStartSuccess.setPid(pid);
      }
      result.setStartSuccess(commandStartSuccess);
    } else {
      result.setStartFailure(
          CommandStartFailure.newBuilder()
              .setException(ErrorModelConverter.toExceptionDetail(exception)));
    }
    return result.build();
  }
}
