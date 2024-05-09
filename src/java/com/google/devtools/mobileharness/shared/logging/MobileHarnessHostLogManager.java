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

package com.google.devtools.mobileharness.shared.logging;

import static com.google.common.base.Preconditions.checkState;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.logging.controller.LogEntryUploadManager;
import com.google.devtools.mobileharness.shared.logging.controller.handler.LocalFileHandlerModule.LocalFileHandlerProvider;
import com.google.devtools.mobileharness.shared.logging.controller.handler.MobileHarnessLogHandler;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/** The manager for managing logs producers and consumers of Mobileharness host side components. */
@Singleton
public class MobileHarnessHostLogManager {

  private static final AtomicBoolean isInitialized = new AtomicBoolean();

  /** For saving strong references of configured loggers. */
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private static final Map<String, Logger> configuredLoggers = new ConcurrentHashMap<>();

  private final LogEntryUploadManager logEntryUploadManager;
  private final MobileHarnessLogHandler mobileHarnessLogHandler;
  private final LocalFileHandlerProvider localFileHandlerProvider;

  @Inject
  MobileHarnessHostLogManager(
      LogEntryUploadManager logEntryUploadManager,
      MobileHarnessLogHandler mobileHarnessLogHandler,
      LocalFileHandlerProvider localFileHandlerProvider) {
    this.logEntryUploadManager = logEntryUploadManager;
    this.mobileHarnessLogHandler = mobileHarnessLogHandler;
    this.localFileHandlerProvider = localFileHandlerProvider;
  }

  /**
   * Initializes the root logger.
   *
   * <ol>
   *   <li>Create the corresponding log directory and logs to files if {@code
   *       localFileHandlerProvider} is provided.
   *   <li>Logs to memory queue and starts the {@code logEntryUploadManager} to periodically upload
   *       logs to the remote if enabled.
   * </ol>
   */
  public void init() throws MobileHarnessException {
    checkState(
        !isInitialized.getAndSet(true), "MobileHarnessHostLogManager has already been initialized");
    Logger rootConfig = getRootLogger();
    localFileHandlerProvider.get().ifPresent(rootConfig::addHandler);
    if (logEntryUploadManager.isEnabled()) {
      try {
        logEntryUploadManager.startAsync().awaitRunning();
        rootConfig.addHandler(mobileHarnessLogHandler);
      } catch (IllegalStateException e) {
        // meets error during startup.
        if (e.getCause() instanceof MobileHarnessException) {
          throw (MobileHarnessException) e.getCause();
        } else {
          throw e;
        }
      }
    }
  }

  /** Shuts down the manager. */
  public void shutDown() {
    if (logEntryUploadManager.isEnabled()) {
      logEntryUploadManager.stopAsync().awaitTerminated();
    }
  }

  private static Logger getRootLogger() {
    return getLoggerByName("");
  }

  private static Logger getLoggerByName(String loggerName) {
    Logger logger = Logger.getLogger(loggerName);
    configuredLoggers.put(loggerName, logger);
    return logger;
  }
}
