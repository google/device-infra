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

package com.google.devtools.mobileharness.infra.ats.console;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.SystemProperties;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Class to store console info. */
@Singleton
public class ConsoleInfo {

  public static final String XTS_TYPE_PROPERTY_KEY = "XTS_TYPE";

  private static final String XTS_ROOT_DIR_PROPERTY_KEY = "XTS_ROOT";
  private static final String PYTHON_PACKAGE_INDEX_URL_PROPERTY_KEY = "PYTHON_PACKAGE_INDEX_URL";

  private final AtomicBoolean shouldExitConsole = new AtomicBoolean(false);
  private final AtomicReference<String> pythonPackageIndexUrl = new AtomicReference<>();

  private final ImmutableMap<String, String> systemProperties;

  private volatile ImmutableList<String> lastCommand = ImmutableList.of();

  @Inject
  @VisibleForTesting
  public ConsoleInfo(@SystemProperties ImmutableMap<String, String> systemProperties) {
    this.systemProperties = systemProperties;
    setPythonPackageIndexUrl(systemProperties.get(PYTHON_PACKAGE_INDEX_URL_PROPERTY_KEY));
  }

  public void setLastCommand(ImmutableList<String> lastCommand) {
    this.lastCommand = lastCommand;
  }

  public ImmutableList<String> getLastCommand() {
    return lastCommand;
  }

  /** Sets whether exit the console. */
  public void setShouldExitConsole(boolean shouldExit) {
    shouldExitConsole.set(shouldExit);
  }

  /** Gets whether exit the console. */
  public boolean getShouldExitConsole() {
    return shouldExitConsole.get();
  }

  /** Gets the xTS root directory. */
  public String getXtsRootDirectoryNonEmpty() {
    String result = systemProperties.get(XTS_ROOT_DIR_PROPERTY_KEY);
    checkState(result != null, "XTS root dir is not specified by -D%s", XTS_ROOT_DIR_PROPERTY_KEY);
    return result;
  }

  /**
   * Gets the xTS type.
   *
   * <p>Please use {@link
   * com.google.devtools.mobileharness.infra.ats.console.util.command.CommandHelper#getXtsType}
   * instead since it can auto detect xTS type if the environment variable is not set.
   */
  public Optional<String> getXtsTypeFromSystemProperty() {
    return Optional.ofNullable(systemProperties.get(XTS_TYPE_PROPERTY_KEY));
  }

  /** Sets the base URL of Python Package Index. */
  public void setPythonPackageIndexUrl(String pythonPackageIndexUrl) {
    this.pythonPackageIndexUrl.set(pythonPackageIndexUrl);
  }

  /** Gets the base URL of Python Package Index. */
  public Optional<String> getPythonPackageIndexUrl() {
    return Optional.ofNullable(pythonPackageIndexUrl.get());
  }
}
