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

package com.google.devtools.mobileharness.shared.util.system;

import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance;
import com.google.devtools.mobileharness.shared.util.network.NetworkUtil;
import javax.inject.Inject;

/** A utility class to print system information. */
public final class SystemInfoPrinter {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final NetworkUtil networkUtil;

  @Inject
  SystemInfoPrinter(NetworkUtil networkUtil) {
    this.networkUtil = networkUtil;
  }

  /** Prints system information. */
  public void printSystemInfo(Importance importance) {
    try {
      logger
          .atInfo()
          .with(IMPORTANCE, importance)
          .log("Hostname: %s", networkUtil.getLocalHostName());
    } catch (MobileHarnessException e) {
      logger
          .atWarning()
          .with(IMPORTANCE, importance)
          .withCause(e)
          .log("Failed to get local hostname");
    }
    logger
        .atInfo()
        .with(IMPORTANCE, importance)
        .log("System Properties: %s", System.getProperties());
    logger.atInfo().with(IMPORTANCE, importance).log("Environment Variables: %s", System.getenv());
    logger.atInfo().with(IMPORTANCE, importance).log("Java version: %s", Runtime.version());
  }
}
