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

package com.google.devtools.mobileharness.shared.util.command.java;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/** Utility class for creating Java command, supporting various Java invocation forms. */
@AutoValue
public abstract class JavaCommandCreator {

  /**
   * Creates a {@link JavaCommandCreator}.
   *
   * @param useStandardInvocationForm it should be always {@code false} for external usage.
   * @param javaLauncherPath must be present if {@code useStandardInvocationForm} is {@code true}.
   *     For example, "{@code java}" or "{@code /usr/local/buildtools/java/jdk/bin/java}".
   */
  public static JavaCommandCreator of(
      boolean useStandardInvocationForm, @Nullable String javaLauncherPath) {
    checkArgument(!useStandardInvocationForm || javaLauncherPath != null);
    return new AutoValue_JavaCommandCreator(
        useStandardInvocationForm, Optional.ofNullable(javaLauncherPath));
  }

  public abstract boolean useStandardInvocationForm();

  /** Must be present if {@link #useStandardInvocationForm()} is {@code true}. */
  public abstract Optional<String> javaLauncherPath();

  /**
   * Creates a Java command.
   *
   * @param jarFilePath the JAR file to execute
   * @param arguments the Java arguments
   * @param nativeArguments the JVM or C++ arguments
   * @return if {@link #useStandardInvocationForm()} is {@code true}, returns {@code
   *     ["<javaLauncherPath>", "<nativeArgument1>", ..., "-jar", "<jarFilePath>", "<argument1>",
   *     ...]}; otherwise returns {@code ["<jarFilePath>", "<nativeArgument1>", ..., "run",
   *     "<argument1>", ...]}
   */
  public List<String> createJavaCommand(
      String jarFilePath, List<String> arguments, List<String> nativeArguments) {
    ImmutableList.Builder<String> result = ImmutableList.builder();
    if (useStandardInvocationForm()) {
      result.add(javaLauncherPath().orElseThrow(AssertionError::new));
      result.addAll(nativeArguments);
      result.add("-jar");
      result.add(jarFilePath);
    } else {
      result.add(jarFilePath);
      result.addAll(nativeArguments);
      result.add("run");
    }
    result.addAll(arguments);
    return result.build();
  }
}
