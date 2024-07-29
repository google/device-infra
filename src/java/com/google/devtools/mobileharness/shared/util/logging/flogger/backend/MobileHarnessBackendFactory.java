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

package com.google.devtools.mobileharness.shared.util.logging.flogger.backend;

import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.system.BackendFactory;
import com.google.errorprone.annotations.Keep;

/**
 * Similar to {@linkplain com.google.common.flogger.backend.system.SimpleBackendFactory
 * SimpleBackendFactory} but support removing context.
 */
public class MobileHarnessBackendFactory extends BackendFactory {

  private static final MobileHarnessBackendFactory INSTANCE = new MobileHarnessBackendFactory();

  @Keep
  public static MobileHarnessBackendFactory getInstance() {
    return INSTANCE;
  }

  @Override
  public LoggerBackend create(String loggingClassName) {
    return new MobileHarnessLoggerBackend(loggingClassName);
  }

  private MobileHarnessBackendFactory() {}
}
