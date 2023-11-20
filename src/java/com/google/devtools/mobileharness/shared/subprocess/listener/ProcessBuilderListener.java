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

package com.google.devtools.mobileharness.shared.subprocess.listener;

import static java.lang.Math.min;
import static java.util.Arrays.asList;

import java.util.List;
import javax.annotation.Nullable;

/**
 * Listener that listening on {@code java.lang.ProcessBuilder} method invocations.
 *
 * <p><b>WARNING</b>: Do <b>NOT</b> let this class depend on <b>ANY</b> dependencies (e.g., Google
 * libraries, your project code, flogger) since this class needs to be loaded by Java bootstrap
 * class loader to instrument classes in the {@code java.lang} package.
 */
public class ProcessBuilderListener {

  private static final ProcessBuilderListener INSTANCE = new ProcessBuilderListener();

  public static ProcessBuilderListener getInstance() {
    return INSTANCE;
  }

  /**
   * Handler for handling process started events.
   *
   * <p>Uses this handler for dependency inversion, because this class needs to be loaded by Java
   * bootstrap class loader but its dependencies can not.
   */
  public interface Handler {

    /** One and only one of {@code process} and {@code exception} is {@code null}. */
    void onProcessStarted(
        List<String> command,
        @Nullable Process process,
        @Nullable Throwable exception,
        List<StackTraceElement> stackTrace);
  }

  private volatile Handler handler;

  private ProcessBuilderListener() {}

  public void setHandler(Handler handler) {
    this.handler = handler;
  }

  /**
   * Invoked when a {@code java.lang.ProcessBuilder.start()} method returns or throws.
   *
   * <p>One and only one of {@code process} and {@code exception} is {@code null}.
   */
  public void onProcessStarted(
      List<String> command, @Nullable Process process, @Nullable Throwable exception) {
    Handler handler = this.handler;
    if (handler != null) {
      List<StackTraceElement> rawStackTrace = asList(Thread.currentThread().getStackTrace());
      // Removes Thread.getStackTrace, ProcessBuilderListener.onProcessStarted,
      // ProcessBuilder.start.
      List<StackTraceElement> stackTrace =
          rawStackTrace.subList(min(3, rawStackTrace.size()), rawStackTrace.size());
      handler.onProcessStarted(command, process, exception, stackTrace);
    }
  }
}
