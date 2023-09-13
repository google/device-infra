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

package com.google.wireless.qa.mobileharness.shared.api;

import com.google.inject.Guice;
import com.google.inject.Injector;

/** The singleton injector used for test runner, drivers and decorators. */
public class TestRunnerInjector {
  private static class SingletonHolder {
    private static final TestRunnerInjector INSTANCE;

    static {
      INSTANCE = new TestRunnerInjector();
    }
  }

  public static Injector getInjector() {
    return SingletonHolder.INSTANCE.getGuiceInjector();
  }

  private final Injector injector;

  private TestRunnerInjector() {
    injector = Guice.createInjector();
  }

  private Injector getGuiceInjector() {
    return injector;
  }
}
