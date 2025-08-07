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

package com.google.devtools.mobileharness.infra.ats.console.util.version;

import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.IMPORTANT;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteInfo;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.nio.file.Path;
import java.util.Optional;
import javax.inject.Inject;

/** Fetch the version of the running tradefed artifacts. */
class VersionParser {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String DEFAULT_IMPLEMENTATION_VERSION = "default";
  private static final String VERSION_FILE = "version.txt";

  private final LocalFileUtil localFileUtil;

  @Inject
  VersionParser(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
  }

  Optional<String> fetchVersion(TestSuiteInfo testSuiteInfo) {
    Package pkg = VersionParser.class.getPackage();
    if (pkg != null) {
      String packageVersion = pkg.getImplementationVersion();
      if (packageVersion != null && !packageVersion.equals(DEFAULT_IMPLEMENTATION_VERSION)) {
        return Optional.of(packageVersion);
      }
    }

    Path toolsDir = testSuiteInfo.getToolsDir();
    Path versionFile = toolsDir.resolve(VERSION_FILE);
    if (localFileUtil.isFileExist(versionFile)) {
      try {
        String version = localFileUtil.readFile(versionFile);
        return Optional.of(version.trim());
      } catch (MobileHarnessException e) {
        logger
            .atWarning()
            .with(IMPORTANCE, IMPORTANT)
            .withCause(e)
            .log("Cannot read %s in directory: %s", VERSION_FILE, toolsDir);
        return Optional.empty();
      }
    } else {
      logger
          .atWarning()
          .with(IMPORTANCE, IMPORTANT)
          .log("Did not find %s in directory: %s", VERSION_FILE, toolsDir);
      return Optional.empty();
    }
  }
}
