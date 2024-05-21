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

import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.util.command.CommandHelper;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteInfo;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.VersionUtil;
import java.nio.file.Path;
import java.util.Optional;
import javax.inject.Inject;

/** Utility for getting version information. */
public class VersionMessageUtil {

  private final ConsoleInfo consoleInfo;
  private final CommandHelper commandHelper;
  private final VersionParser versionParser;

  @Inject
  VersionMessageUtil(
      ConsoleInfo consoleInfo, CommandHelper commandHelper, VersionParser versionParser) {
    this.consoleInfo = consoleInfo;
    this.commandHelper = commandHelper;
    this.versionParser = versionParser;
  }

  /**
   * Returns a version message like:
   *
   * <pre>{@code
   * Android Compatibility Test Suite 14_r2 (11179914)
   * OmniLab lab-4.268.0 github-fece030c657cddecc2cf7a122aa1fc4976dfed9c
   * }</pre>
   */
  public String getVersionMessage() {
    Path xtsRootDir = consoleInfo.getXtsRootDirectoryNonEmpty();
    String xtsType = commandHelper.getXtsType();
    TestSuiteInfo testSuiteInfo = TestSuiteInfo.getInstance(xtsRootDir.toString(), xtsType);
    String buildNumber =
        versionParser.fetchVersion(testSuiteInfo).orElseGet(testSuiteInfo::getBuildNumber);
    String labVersion = Version.LAB_VERSION.toString();
    Optional<String> gitHubVersion = VersionUtil.getGitHubVersion();
    return String.format(
        "Android %s %s (%s)\nOmniLab lab-%s%s",
        testSuiteInfo.getFullName(),
        testSuiteInfo.getVersion(),
        buildNumber,
        labVersion,
        gitHubVersion.map(version -> String.format(" github-%s", version)).orElse(""));
  }
}
