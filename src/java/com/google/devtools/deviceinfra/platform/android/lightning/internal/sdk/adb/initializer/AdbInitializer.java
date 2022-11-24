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

package com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.initializer;

import com.google.common.base.Suppliers;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.platform.android.sdktool.proto.Adb.AdbParam;
import java.util.function.Supplier;

/**
 * ADB initializer for initializing ADB tools for a machine.
 *
 * <p>It does the initialization work <b>only once</b> for a process at the <b>first</b> time {@link
 * #initializeAdbEnvironment()} is invoked.
 *
 * <p><b>WARNING</b>: If a process does the initialization work, all running ADB commands on the
 * same machine will be affected.
 */
public class AdbInitializer {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Supplier<AdbParam> ADB_PARAM_SUPPLIER =
      Suppliers.memoize(AdbInitializer::initializeAdb);

  private static AdbParam initializeAdb() {
    logger.atInfo().log("Initializing ADB with adb initializer...");
    return getAdbInitializeTemplate().initializeAdb();
  }

  /**
   * Initializes ADB environment and returns {@link AdbParam} storing the info.
   *
   * <p>Invocations after the first call will return the cached info.
   */
  public AdbParam initializeAdbEnvironment() {
    return ADB_PARAM_SUPPLIER.get();
  }

  private static AdbInitializeTemplate getAdbInitializeTemplate() {
    return new AdbInitializeTemplateImpl();
  }
}
