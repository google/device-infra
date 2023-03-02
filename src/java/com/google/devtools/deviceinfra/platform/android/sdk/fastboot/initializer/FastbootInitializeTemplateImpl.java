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

package com.google.devtools.deviceinfra.platform.android.sdk.fastboot.initializer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;

/** Fastboot initializer for initializing fastboot tools for a machine. */
public class FastbootInitializeTemplateImpl extends FastbootInitializeTemplate {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LocalFileUtil localFileUtil;

  public FastbootInitializeTemplateImpl() {
    this(new LocalFileUtil());
  }

  @VisibleForTesting
  FastbootInitializeTemplateImpl(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
  }

  @Override
  public FastbootParam initializeFastboot() {
    String fastbootPath = getFastbootPathFromUser();

    if (fastbootPath.isEmpty()) {
      logger.atInfo().log(
          "Fastboot binary path --fastboot not specified, use \"fastboot\" as fastboot path");
      fastbootPath = "fastboot";
    } else {
      logger.atInfo().log("Fastboot binary path from user: %s", fastbootPath);
    }

    if (localFileUtil.isFileExistInPath(fastbootPath)) {
      return FastbootParam.builder().setFastbootPath(fastbootPath).build();
    } else {
      String error =
          String.format(
              "Invalid fastboot path [%s] (file doesn't exist or isn't in PATH dirs)",
              fastbootPath);
      return FastbootParam.builder().setInitializationError(error).build();
    }
  }
}
