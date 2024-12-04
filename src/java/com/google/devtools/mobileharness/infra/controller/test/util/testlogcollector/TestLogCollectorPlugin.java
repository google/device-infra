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

package com.google.devtools.mobileharness.infra.controller.test.util.testlogcollector;

import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.constant.closeable.NonThrowingAutoCloseable;
import com.google.devtools.mobileharness.shared.util.command.linecallback.CommandOutputLogger;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessLogger;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestEndingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin;
import java.util.Objects;

/** A lab plugin to collect Java logger logs of a local test runner into a file. */
@Plugin(type = Plugin.PluginType.LAB)
public final class TestLogCollectorPlugin {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private volatile NonThrowingAutoCloseable logHandlerRemover;

  public TestLogCollectorPlugin() {}

  @Subscribe
  public void onTestStarting(TestStartingEvent event) throws MobileHarnessException {
    logHandlerRemover =
        MobileHarnessLogger.addSingleFileHandler(
            event.getTest().getGenFileDir(),
            "local_test_log.txt",
            logRecord ->
                !Objects.equals(
                    logRecord.getSourceClassName(), CommandOutputLogger.class.getName()));
    logger.atInfo().log("Test log collector plugin started to collect logs.");
  }

  @Subscribe
  public void onTestEnding(TestEndingEvent event) {
    if (logHandlerRemover != null) {
      logger.atInfo().log("Test log collector plugin closed the log handler.");
      logHandlerRemover.close();
      logHandlerRemover = null;
    }
  }
}
