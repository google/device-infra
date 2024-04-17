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

package com.google.devtools.deviceinfra.shared.logging.util;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.api.MonitoredResource;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.LogSite;
import com.google.common.flogger.context.Tags;
import com.google.devtools.deviceinfra.shared.logging.parameter.LogEnvironment;
import com.google.devtools.deviceinfra.shared.logging.parameter.LogProject;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.network.NetworkUtil;
import com.google.inject.Inject;
import com.google.logging.type.LogSeverity;
import com.google.logging.v2.LogEntry;
import com.google.logging.v2.LogEntrySourceLocation;
import com.google.protobuf.util.Timestamps;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/** The utility of parsing a java {@link LogRecord} to a Stackdriver {@link LogEntry}. */
public class LogEntryUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // The max length of the text payload is 8K.
  private static final int TEXT_PAYLOAD_MAX_LENGTH = 8192;

  private static final String LABEL_HOST_NAME = "host_name";
  private static final String LABEL_ENV = "env";

  /** The map from google logger level to stackdriver logger level. */
  private static final ImmutableMap<Level, LogSeverity> LOG_LEVEL_MAP =
      new ImmutableMap.Builder<Level, LogSeverity>()
          .put(Level.SEVERE, LogSeverity.EMERGENCY)
          .put(Level.WARNING, LogSeverity.WARNING)
          .put(Level.INFO, LogSeverity.INFO)
          .put(Level.CONFIG, LogSeverity.INFO)
          .put(Level.FINE, LogSeverity.DEBUG)
          .put(Level.FINER, LogSeverity.DEBUG)
          .put(Level.FINEST, LogSeverity.DEBUG)
          .buildOrThrow();

  private final LogProject logProject;
  private final LogEnvironment logEnvironment;

  private static class LazyInitializer {
    private static final String HOST_NAME;

    static {
      String hostName = "UNKNOWN_HOST_NAME";
      try {
        hostName = new NetworkUtil().getLocalHostName();
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log("Failed to get host name.");
      }
      HOST_NAME = hostName;
    }
  }

  @Inject
  LogEntryUtil(LogProject logProject, LogEnvironment logEnvironment) {
    this.logProject = logProject;
    this.logEnvironment = logEnvironment;
  }

  /** Generates the {@link LogEntry} from {@link LogRecord}. */
  public LogEntry generateLogEntry(LogRecord logRecord) {
    LogEntry.Builder logEntryBuilder = LogEntry.newBuilder();
    // Fills the log entry source.
    LogEntrySourceLocation.Builder logEntrySourceLocationBuilder =
        LogEntrySourceLocation.newBuilder();
    LogSite logSite = LogDataExtractor.getLogSite(logRecord);
    logEntrySourceLocationBuilder
        .setFile(logSite.getFileName() != null ? logSite.getFileName() : "<unknown file>")
        .setFunction(logSite.getMethodName())
        .setLine(logSite.getLineNumber());
    // Fills the labels.
    Map<String, String> labels = new HashMap<>();
    Tags tags = LogDataExtractor.getTags(logRecord);
    if (tags != null) {
      tags.asMap()
          .forEach(
              (key, values) -> {
                String stringValue =
                    Joiner.on('|')
                        .join(
                            values.stream()
                                .filter(value -> value instanceof String)
                                .collect(toImmutableList()));
                if (!Strings.isNullOrEmpty(stringValue)) {
                  labels.put(key, stringValue);
                }
              });
    }
    labels.put(LABEL_HOST_NAME, LazyInitializer.HOST_NAME);
    if (logEnvironment != LogEnvironment.UNKNOWN) {
      labels.put(LABEL_ENV, logEnvironment.getName());
    }
    String textPayLoad = logRecord.getMessage();
    Throwable throwable = logRecord.getThrown();
    if (throwable != null) {
      textPayLoad =
          String.format("%s\n%s", textPayLoad, Throwables.getStackTraceAsString(throwable));
    }
    return logEntryBuilder
        .setLogName(logProject.getLogName())
        .setTextPayload(StrUtil.truncateAtMaxLength(textPayLoad, TEXT_PAYLOAD_MAX_LENGTH, true))
        .setSeverity(LOG_LEVEL_MAP.getOrDefault(logRecord.getLevel(), LogSeverity.INFO))
        .setTimestamp(Timestamps.fromMillis(logRecord.getMillis()))
        .setSourceLocation(logEntrySourceLocationBuilder)
        .setResource(MonitoredResource.newBuilder().setType(logProject.getResourceType()))
        .putAllLabels(labels)
        .build();
  }
}
