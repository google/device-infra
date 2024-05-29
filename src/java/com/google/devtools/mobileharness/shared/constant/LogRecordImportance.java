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

package com.google.devtools.mobileharness.shared.constant;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.MetadataKey;
import com.google.devtools.mobileharness.shared.constant.closeable.NonThrowingAutoCloseable;
import com.google.devtools.mobileharness.shared.util.base.StackSet;
import com.google.devtools.mobileharness.shared.util.logging.LogDataExtractor;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.LogRecord;

/** Constants of importance of log records. */
public class LogRecordImportance {

  /** Values of importance. */
  public enum Importance {
    DEBUG(50),

    // Importance >= 100
    // ATS console logs whose importance is not less than it will be shown in ATS console.

    /** Logs from flogger have this importance by default. */
    NORMAL(100),

    // Importance >= 150
    // OLC server logs whose importance is not less than it will be shown in ATS console.

    /** Logs from TestInfo/JobInfo log() have this importance by default. */
    TEST_INFO(150),

    IMPORTANT(200),

    /** Logs from TF process have this importance. */
    TF(300);

    private final int value;

    Importance(int value) {
      this.value = value;
    }

    public int value() {
      return value;
    }
  }

  public static final MetadataKey<Importance> IMPORTANCE =
      MetadataKey.single("importance", Importance.class);

  /**
   * Set the default log record importance of logs generated between this scope is created and
   * closed in the same thread (including logs generated from invoked methods).
   *
   * <p>Example:
   *
   * <pre>{@code
   * try (var ignored = new LogImportanceScope(IMPORTANT, logRecord -> true)) {
   *   ... // All log statements in this scope will have importance IMPORTANT.
   * }
   * }</pre>
   */
  public static class LogImportanceScope implements NonThrowingAutoCloseable {

    private static final ThreadLocal<StackSet<LogImportanceScope>> SCOPES =
        ThreadLocal.withInitial(StackSet::new);

    @VisibleForTesting
    static Optional<Importance> getCurrentImportance(LogRecord logRecord) {
      return SCOPES.get().getLast().flatMap(scope -> scope.importanceForLogRecord(logRecord));
    }

    private final Importance importance;
    private final Predicate<LogRecord> logRecordFilter;

    public LogImportanceScope(Importance importance, Predicate<LogRecord> logRecordFilter) {
      this.importance = checkNotNull(importance);
      this.logRecordFilter = checkNotNull(logRecordFilter);
      SCOPES.get().add(this);
    }

    @Override
    public void close() {
      SCOPES.get().removeUntilLast(this);
    }

    private Optional<Importance> importanceForLogRecord(LogRecord logRecord) {
      if (logRecordFilter.test(logRecord)) {
        return Optional.of(importance);
      } else {
        return Optional.empty();
      }
    }
  }

  public static Importance getLogRecordImportance(LogRecord logRecord) {
    return LogDataExtractor.getSingleMetadataValue(logRecord, LogRecordImportance.IMPORTANCE)
        .or(() -> LogImportanceScope.getCurrentImportance(logRecord))
        .orElse(Importance.NORMAL);
  }

  private LogRecordImportance() {}
}
