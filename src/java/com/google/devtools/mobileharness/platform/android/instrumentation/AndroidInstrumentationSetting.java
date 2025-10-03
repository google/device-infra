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

package com.google.devtools.mobileharness.platform.android.instrumentation;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import java.util.Map;
import javax.annotation.Nullable;

/** Auto_Value for instrumentation settings. */
@AutoValue
public abstract class AndroidInstrumentationSetting {

  /** The creator. */
  public static AndroidInstrumentationSetting create(
      String packageName,
      String runnerName,
      @Nullable String className,
      @Nullable Map<String, String> otherOptions,
      boolean async,
      boolean showRawResults,
      boolean prefixAndroidTest,
      boolean noIsolatedStorage,
      boolean useTestStorageService,
      boolean enableCoverage) {
    return create(
        packageName,
        runnerName,
        className,
        otherOptions,
        async,
        showRawResults,
        prefixAndroidTest,
        noIsolatedStorage,
        useTestStorageService,
        enableCoverage,
        /* useOrchestrator= */ false);
  }

  public static AndroidInstrumentationSetting create(
      String packageName,
      String runnerName,
      @Nullable String className,
      @Nullable Map<String, String> otherOptions,
      boolean async,
      boolean showRawResults,
      boolean prefixAndroidTest,
      boolean noIsolatedStorage,
      boolean useTestStorageService,
      boolean enableCoverage,
      boolean useOrchestrator) {
    return new AutoValue_AndroidInstrumentationSetting(
        checkNotNull(packageName),
        checkNotNull(runnerName),
        className,
        otherOptions,
        async,
        showRawResults,
        prefixAndroidTest,
        noIsolatedStorage,
        useTestStorageService,
        enableCoverage,
        useOrchestrator);
  }

  /** The Android package name of the test package. */
  public abstract String packageName();

  /** The class name of the instrumented test runner you are using. */
  public abstract String runnerName();

  /**
   * A fully-qualified test case class name, or className#methodName to specify only one method of
   * the class, or a comma separated list of them, or null.
   */
  @Nullable
  public abstract String className();

  /** Other am instrumentation options excluding the class option, or null. */
  @Nullable
  public abstract Map<String, String> otherOptions();

  /** Whether to async run the instrumentation. */
  public abstract boolean async();

  /** Whether to print raw results rather than stream result. */
  public abstract boolean showRawResults();

  /** Whether to add prefix parameter before start am instrumentation. */
  public abstract boolean prefixAndroidTest();

  /** Whether to whether to disable isolated-storage for instrumentation command. */
  public abstract boolean noIsolatedStorage();

  /** Whether to set useTestStorageService in instrumentation command */
  public abstract boolean useTestStorageService();

  /** Whether to enable coverage for the instrumentation command. */
  public abstract boolean enableCoverage();

  /** Whether to use orchestrator to run tests. */
  public abstract boolean useOrchestrator();
}
