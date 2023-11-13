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

package com.google.wireless.qa.mobileharness.shared;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.logging.MobileHarnessLogFormatter;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.constant.DirCommon;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.FileHandler;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Helper class for configuring {@link java.util.logging.Logger} with the special format of
 * MobileHarness.
 */
public class MobileHarnessLogger {

  /**
   * The className -> logLevel mappings to set different log levels for different classes. By
   * default, the log level for a class is INFO, so we can't see logs in CONFIG, FINE, FINER and
   * FINEST levels. e.g. "com.google.wireless.qa.mobileharness.shared.MobileHarnessLogger" ->
   * Level.FINE.
   *
   * <p>Note: you must explicitly set levels for classes and call {@link MobileHarnessLogger#init}
   * if you want to view logs in a level higher than INFO in debug mode.
   */
  private static final ImmutableMap<String, Level> DEBUG_LOG_LEVELS = ImmutableMap.of();

  /** Max size of a single log file. Here we set it to 10 MB. */
  private static final int LOG_FILE_SIZE_LIMIT = 10 * 1024 * 1024;

  private static final Filter COMMON_FILTER =
      logRecord -> !logRecord.getLoggerName().equals("io.grpc.netty.NettyServerHandler");

  private static final Filter LAB_FILTER =
      logRecord -> COMMON_FILTER.isLoggable(logRecord) && !(false);

  private static final Filter FILTER = LAB_FILTER;

  private static final AtomicBoolean isInitialized = new AtomicBoolean();

  @Nullable private static volatile String logFileDirName;

  /** For saving strong references of configured loggers. */
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private static final Map<String, Logger> configuredLoggers = new ConcurrentHashMap<>();

  private MobileHarnessLogger() {}

  /** Initializes the root logger and does not log to file. */
  public static void init() {
    init(null /* logFileDirPattern */);
  }

  /** Initializes the root logger. */
  public static void init(@Nullable String logFileDirName) {
    init(logFileDirName, ImmutableList.of());
  }

  /**
   * Initializes the root logger. Logs to file according to the log file dir pattern and {@link
   * DirCommon#DEFAULT_LOG_FILE_NAME} if the log file dir pattern is not empty or null.
   *
   * <p>The parent dir will be created if necessary.
   *
   * @see FileHandler
   */
  public static void init(@Nullable String logFileDirName, ImmutableList<Handler> otherHandlers) {
    checkState(
        !isInitialized.getAndSet(true), "Mobile Harness logger has already been initialized");
    MobileHarnessLogger.logFileDirName = logFileDirName;
    if (Flags.instance().enableDebugMode.getNonNull()) {
      DEBUG_LOG_LEVELS.forEach((className, level) -> getLoggerByName(className).setLevel(level));
    }
    Logger rootLogger = getLoggerByName("");
    rootLogger.setLevel(Level.ALL);
    try {
      createFileHandler(
              /* subDir= */ "",
              DirCommon.DEFAULT_LOG_FILE_NAME,
              Flags.instance().logFileNum.getNonNull())
          .ifPresent(rootLogger::addHandler);
    } catch (MobileHarnessException e) {
      throw new IllegalArgumentException(e);
    }
    otherHandlers.forEach(rootLogger::addHandler);

    for (Handler handler : rootLogger.getHandlers()) {
      handler.setFormatter(MobileHarnessLogFormatter.getDefaultFormatter());
      handler.setFilter(FILTER);
      handler.setLevel(Level.INFO);
    }
  }

  /**
   * See {@link MobileHarnessLogFormatter#getDateTimeFormatter()}.
   *
   * @return the default formatter for printing the date time in the log
   */
  public static DateTimeFormatter getDateTimeFormatter() {
    return MobileHarnessLogFormatter.getDateTimeFormatter();
  }

  /**
   * Removes an additional handler from logger.
   *
   * @param handler the handler to be removed from logger, if null nothing will be removed
   */
  public static void removeHandler(@Nullable Class<?> loggerClass, @Nullable Handler handler) {
    if (handler != null) {
      getLoggerByClass(loggerClass).removeHandler(handler);
    }
  }

  /**
   * Specify whether this logger should send its output to its parent Logger.
   *
   * @see java.util.logging.Logger#setUseParentHandlers(boolean)
   */
  public static void useParentHandlers(Class<?> loggerClass, boolean useParentHandlers) {
    if (loggerClass != null) {
      getLoggerByClass(loggerClass).setUseParentHandlers(useParentHandlers);
    }
  }

  private static Optional<Handler> createFileHandler(
      String subDir, String logFileNamePattern, int fileNum) throws MobileHarnessException {
    String logFileDirName = MobileHarnessLogger.logFileDirName;
    if (Strings.isNullOrEmpty(logFileDirName)) {
      return Optional.empty();
    }
    String logDir = PathUtil.join(logFileDirName, subDir);
    LocalFileUtil localFileUtil = new LocalFileUtil();
    localFileUtil.prepareDir(logDir);
    localFileUtil.grantFileOrDirFullAccess(logDir);
    String logFilePattern = PathUtil.join(logDir, logFileNamePattern);
    try {
      return Optional.of(new FileHandler(logFilePattern, LOG_FILE_SIZE_LIMIT, fileNum));
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.MOBILE_HARNESS_LOGGER_CREATE_FILE_HANDLER_ERROR,
          "Failed to create file handler",
          e);
    }
  }

  private static Logger getLoggerByClass(@Nullable Class<?> loggerClass) {
    return getLoggerByName(loggerClass == null ? "" : loggerClass.getName().replace('$', '.'));
  }

  private static Logger getLoggerByName(String loggerName) {
    Logger logger = Logger.getLogger(loggerName);
    configuredLoggers.put(loggerName, logger);
    return logger;
  }
}
