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

package com.google.devtools.mobileharness.shared.util.comm.stub;

import com.google.devtools.mobileharness.shared.util.comm.stub.Annotations.AsyncTransformExecution;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.inject.AbstractModule;
import java.util.concurrent.Executor;

/** Module to provide the async transform executor. */
public final class FutureExecutorModule extends AbstractModule {

  private static final Executor DEFAULT_EXECUTOR =
      ThreadPools.createStandardThreadPool("async-transform-executor");

  @Override
  protected void configure() {
    bind(Executor.class).annotatedWith(AsyncTransformExecution.class).toInstance(getExecutor());
  }

  public static Executor getExecutor() {
    return DEFAULT_EXECUTOR;
  }
}
