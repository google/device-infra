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

import com.google.common.base.Suppliers;
import com.google.common.flogger.FluentLogger;
import java.util.function.Supplier;

/**
 * Fastboot initializer for initializing fastboot tools for a machine.
 *
 * <p>It does the initialization work <b>only once</b> for a process at the <b>first</b> time {@link
 * #initializeFastbootEnvironment()} is invoked.
 */
public class FastbootInitializer {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Supplier<FastbootParam> FASTBOOT_PARAM_SUPPLIER =
      Suppliers.memoize(FastbootInitializer::initializeFastboot);

  private static FastbootParam initializeFastboot() {
    logger.atInfo().log("Initializing fastboot tools with fastboot initializer...");
    return getFastbootInitializeTemplate().initializeFastboot();
  }

  /**
   * Initializes fastboot environment and returns {@link FastbootParam} storing the info.
   *
   * <p>Invocations after the first call will return the cached info.
   */
  public FastbootParam initializeFastbootEnvironment() {
    return FASTBOOT_PARAM_SUPPLIER.get();
  }

  private static FastbootInitializeTemplate getFastbootInitializeTemplate() {
    return new FastbootInitializeTemplateImpl();
  }
}
