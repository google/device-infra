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

package com.google.devtools.deviceinfra.host.utrs;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.devtools.deviceinfra.host.utrs.Annotations.DebugThreadPool;
import com.google.devtools.deviceinfra.host.utrs.Annotations.DeviceManagerThreadPool;
import com.google.devtools.deviceinfra.host.utrs.Annotations.FileResolverThreadPool;
import com.google.devtools.deviceinfra.host.utrs.Annotations.LabServerRpcThreadPool;
import com.google.devtools.deviceinfra.host.utrs.Annotations.LocalGrpcThreadPool;
import com.google.devtools.deviceinfra.host.utrs.Annotations.MainThreadPool;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadFactoryUtil;
import com.google.inject.AbstractModule;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

/** Module class that instantiates all thread factories used by UTRS. */
public class UtrsThreadPoolFactoryModule extends AbstractModule {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  protected void configure() {
    bind(ListeningExecutorService.class)
        .annotatedWith(MainThreadPool.class)
        .toInstance(createThreadPool("mh-lab-server-main-thread"));
    bind(ListeningExecutorService.class)
        .annotatedWith(DeviceManagerThreadPool.class)
        .toInstance(createThreadPool("mh-device-manager-device-thread"));
    bind(ListeningExecutorService.class)
        .annotatedWith(LabServerRpcThreadPool.class)
        .toInstance(createThreadPool("mh-lab-server-rpc-thread"));
    bind(ListeningExecutorService.class)
        .annotatedWith(LocalGrpcThreadPool.class)
        .toInstance(createThreadPool("mh-lab-server-local-grpc-thread"));

    bind(ListeningScheduledExecutorService.class)
        .annotatedWith(DebugThreadPool.class)
        .toInstance(
            MoreExecutors.listeningDecorator(
                new ScheduledThreadPoolExecutor(
                    1, createThreadFactory("mh-lab-server-debug-random-exit-task"))));
    bind(ListeningExecutorService.class)
        .annotatedWith(FileResolverThreadPool.class)
        .toInstance(
            MoreExecutors.listeningDecorator(
                Executors.newCachedThreadPool(createThreadFactory("file-resolver-thread"))));
  }

  private static ListeningExecutorService createThreadPool(String threadNamePrefix) {
    return MoreExecutors.listeningDecorator(
        Executors.newCachedThreadPool(ThreadFactoryUtil.createThreadFactory(threadNamePrefix)));
  }

  /**
   * Returns a {@link ThreadFactory} which {@link Thread#setDefaultUncaughtExceptionHandler} with a
   * logging handler and does not {@link Thread#setDaemon} with {@code true}.
   *
   * @param threadNamePrefix e.g., "mh-lab-server-main-thread" will generate thread names like
   *     "mh-lab-server-main-thread-1", "mh-lab-server-main-thread-2", etc.
   */
  private static ThreadFactory createThreadFactory(String threadNamePrefix) {
    return new ThreadFactoryBuilder()
        .setNameFormat(threadNamePrefix + "-%d")
        .setUncaughtExceptionHandler(
            (thread, throwable) ->
                logger.atSevere().withCause(throwable).log(
                    "Uncaught exception from thread [%s]", thread.getName()))
        .build();
  }
}
