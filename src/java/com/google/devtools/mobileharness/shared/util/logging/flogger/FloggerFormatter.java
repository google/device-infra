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

package com.google.devtools.mobileharness.shared.util.logging.flogger;

import com.google.devtools.mobileharness.shared.util.logging.flogger.backend.MobileHarnessBackendFactory;

/** Utility for configuring formats of flogger. */
public class FloggerFormatter {

  private static final String OSS_BACKEND_FACTORY_KEY = "flogger.backend_factory";
  private static final String OSS_BACKEND_FACTORY = MobileHarnessBackendFactory.class.getName();

  private static final String CUSTOM_FORMATTER_TEMPLATE_KEY = "google.debug_logs.format_template";
  private static final String CUSTOM_FORMATTER_TEMPLATE_WITHOUT_CONTEXT = "${message}";
  private static final String CUSTOM_FORMATTER_TEMPLATE_WITH_CONTEXT =
      String.format(
          "%s${!metadata/%s/%s}",
          CUSTOM_FORMATTER_TEMPLATE_WITHOUT_CONTEXT,
          FloggerFormatterConstants.CONTEXT_PREFIX,
          FloggerFormatterConstants.CONTEXT_SUFFIX);

  /**
   * Note that this method must be called before flogger backend classes are loaded in the current
   * process. In most cases, it means this method should be called in the first statement of a
   * static initializer blocker of the main class of the current process, and the static initializer
   * blocker should be before the logger field declaration of the main class.
   *
   * <p>For example,
   *
   * <pre>{@code
   * public class Main {
   *
   *   static {
   *     FloggerFormatter.initialize();
   *   }
   *
   *   private static final FluentLogger logger = FluentLogger.forEnclosingClass();
   *
   *   public static void main(String[] args) {
   *     ...
   *   }
   * }
   * }</pre>
   */
  public static void initialize() {
    // For DefaultPlatform.
    System.setProperty(OSS_BACKEND_FACTORY_KEY, OSS_BACKEND_FACTORY);

    // For GooglePlatform.
    boolean withContext = FloggerFormatterConstants.withContext();
    if (System.getProperty(CUSTOM_FORMATTER_TEMPLATE_KEY) == null) {
      System.setProperty(
          CUSTOM_FORMATTER_TEMPLATE_KEY, getSimpleCustomFormatterTemplate(withContext));
    }
  }

  private static String getSimpleCustomFormatterTemplate(boolean withContext) {
    return withContext
        ? CUSTOM_FORMATTER_TEMPLATE_WITH_CONTEXT
        : CUSTOM_FORMATTER_TEMPLATE_WITHOUT_CONTEXT;
  }

  private FloggerFormatter() {}
}
