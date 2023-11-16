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

package com.google.devtools.mobileharness.infra.ats.console.util.tradefed;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadFactoryUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.util.concurrent.Executors;
import javax.inject.Singleton;

/** Test Guice bindings. */
final class TestModule extends AbstractModule {

  @Provides
  @Singleton
  ListeningExecutorService provideThreadPool() {
    return MoreExecutors.listeningDecorator(
        Executors.newCachedThreadPool(
            ThreadFactoryUtil.createThreadFactory("test-thread-pool", /* daemon= */ true)));
  }
}
