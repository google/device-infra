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

package com.google.devtools.mobileharness.infra.client.api;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.infra.client.api.Annotations.EnvThreadPool;
import com.google.devtools.mobileharness.infra.client.api.Annotations.ExtraGlobalInternalPlugins;
import com.google.devtools.mobileharness.infra.client.api.Annotations.ExtraJobInternalPlugins;
import com.google.devtools.mobileharness.infra.client.api.Annotations.JobThreadPool;
import com.google.devtools.mobileharness.infra.client.api.Annotations.ShutdownJobThreadWhenShutdownProcess;
import com.google.devtools.mobileharness.infra.client.api.plugin.GenFileHandler;
import com.google.devtools.mobileharness.shared.context.InvocationContextExecutors;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadFactoryUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.util.concurrent.Executors;
import javax.inject.Singleton;

/** Module for {@link ClientApi}. */
public class ClientApiModule extends AbstractModule {

  @Provides
  @Singleton
  @EnvThreadPool
  ListeningExecutorService provideEnvThreadPool() {
    return InvocationContextExecutors.propagatingContext(
        MoreExecutors.listeningDecorator(
            Executors.newCachedThreadPool(ThreadFactoryUtil.createThreadFactory("env-thread"))),
        ListeningExecutorService.class);
  }

  @Provides
  @Singleton
  @JobThreadPool
  ListeningExecutorService provideJobThreadPool() {
    return InvocationContextExecutors.propagatingContext(
        MoreExecutors.listeningDecorator(
            Executors.newCachedThreadPool(ThreadFactoryUtil.createThreadFactory("job-thread"))),
        ListeningExecutorService.class);
  }

  @Provides
  @ExtraGlobalInternalPlugins
  ImmutableList<Object> provideExtraGlobalInternalPlugins() {
    return ImmutableList.of(new GenFileHandler());
  }

  @Provides
  @ExtraJobInternalPlugins
  ImmutableList<Object> provideExtraJobInternalPlugins() {
    return ImmutableList.of();
  }

  @Provides
  @ShutdownJobThreadWhenShutdownProcess
  boolean provideShutdownJobThreadWhenShutdownProcess() {
    return true;
  }
}
