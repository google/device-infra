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
import com.google.devtools.deviceinfra.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.util.Optional;

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
  protected Optional<FastbootParam> initializeFastbootToolsFromUser() {
    String fastbootPath = getFastbootPathFromUser();
    try {
      checkFastbootToolsInDir(PathUtil.dirname(fastbootPath));
      logger.atInfo().log("Path of fastboot binary from user: %s", fastbootPath);
      return Optional.of(FastbootParam.builder().setFastbootPath(fastbootPath).build());
    } catch (MobileHarnessException e) {
      String error =
          String.format(
              "Error when checking given fastboot tools, please point --fastboot to a valid"
                  + " fastboot binary:\n%s",
              e.getMessage());
      return Optional.of(FastbootParam.builder().setInitializationError(error).build());
    }
  }

  private void checkFastbootToolsInDir(String dir) throws MobileHarnessException {
    localFileUtil.checkFile(PathUtil.join(dir, "fastboot"));
  }
}
