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

package com.google.devtools.mobileharness.shared.version;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Suppliers;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.function.Supplier;

/** Utility for getting version information. */
public class VersionUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String GITHUB_VERSION_RESOURCE_FILE_PATH = "/deviceinfra_github_version.txt";

  private static final Supplier<Optional<String>> GITHUB_VERSION_SUPPLIER =
      Suppliers.memoize(VersionUtil::doGetGitHubVersion);

  /**
   * Gets the GitHub latest commit ID from which the jar of the current process was built, if any.
   *
   * @implSpec reads the commit ID from {@link #GITHUB_VERSION_RESOURCE_FILE_PATH}
   */
  public static Optional<String> getGitHubVersion() {
    return GITHUB_VERSION_SUPPLIER.get();
  }

  private static Optional<String> doGetGitHubVersion() {
    try (InputStream inputStream =
        VersionUtil.class.getResourceAsStream(GITHUB_VERSION_RESOURCE_FILE_PATH)) {
      if (inputStream == null) {
        return Optional.empty();
      }
      return Optional.of(CharStreams.toString(new InputStreamReader(inputStream, UTF_8)).trim());
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Failed to get GitHub version");
      return Optional.empty();
    }
  }

  private VersionUtil() {}
}
