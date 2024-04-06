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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.inject.Qualifier;

/** Annotations for ATS console. */
public class Annotations {

  /** ID of the console. */
  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  public @interface ConsoleId {}

  /** Line reader for the console. */
  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  public @interface ConsoleLineReader {}

  /** Output of the console. */
  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  public @interface ConsoleOutput {

    /** Type of {@link ConsoleOutput}. */
    enum Type {
      OUT_STREAM,
      ERR_STREAM,
      OUT_WRITER,
      ERR_WRITER,
    }

    Type value();
  }

  /** Main arguments of the console. */
  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  public @interface MainArgs {}

  /** Whether to parse command only, or to run the command. */
  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  public @interface ParseCommandOnly {}

  /**
   * A future for RunCommand to set command line parse result, which later can be used by caller of
   * RunCommand.
   */
  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  public @interface RunCommandParsingResultFuture {}

  /** System properties. */
  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  public @interface SystemProperties {}

  private Annotations() {}
}
