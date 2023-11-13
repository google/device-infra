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

package com.google.devtools.mobileharness.infra.client.longrunningservice;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.infra.client.api.Annotations.GlobalInternalEventBus;
import com.google.devtools.mobileharness.infra.client.api.ClientApi;
import com.google.devtools.mobileharness.infra.client.api.ClientApiModule;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.api.mode.local.LocalMode;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.ServerStartTime;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.ControllerModule;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogManager.LogRecordsCollector;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecords;
import com.google.devtools.mobileharness.infra.controller.test.util.SubscriberExceptionLoggingHandler;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadFactoryUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import java.time.Instant;
import java.util.concurrent.Executors;
import javax.inject.Singleton;

/** Module for OLC server. */
class ServerModule extends AbstractModule {

  private final Instant serverStartTime;

  ServerModule(Instant serverStartTime) {
    this.serverStartTime = serverStartTime;
  }

  @Override
  protected void configure() {
    install(new ControllerModule());
    install(new ClientApiModule());

    bind(ClientApi.class).in(Scopes.SINGLETON);
  }

  @Provides
  @ServerStartTime
  Instant provideServerStartTime() {
    return serverStartTime;
  }

  @Provides
  @Singleton
  LocalMode provideLocalMode() {
    return new LocalMode();
  }

  @Provides
  @Singleton
  DeviceQuerier provideDeviceQuerier(LocalMode localMode) {
    return localMode.createDeviceQuerier();
  }

  @Provides
  @Singleton
  ListeningExecutorService provideThreadPool() {
    return MoreExecutors.listeningDecorator(
        Executors.newCachedThreadPool(ThreadFactoryUtil.createThreadFactory("main-thread")));
  }

  @Provides
  @Singleton
  ListeningScheduledExecutorService provideScheduledThreadPool() {
    return MoreExecutors.listeningDecorator(
        Executors.newScheduledThreadPool(
            /* corePoolSize= */ 10, ThreadFactoryUtil.createThreadFactory("main-thread")));
  }

  @Provides
  Sleeper provideSleeper() {
    return Sleeper.defaultSleeper();
  }

  @Provides
  @Singleton
  @GlobalInternalEventBus
  EventBus provideGlobalInternalEventBus() {
    return new EventBus(new SubscriberExceptionLoggingHandler());
  }

  @Provides
  LogRecordsCollector<GetLogResponse> provideLogRecordsCollector() {
    return new GetLogResponseLogRecordsCollector();
  }

  private static class GetLogResponseLogRecordsCollector
      implements LogRecordsCollector<GetLogResponse> {

    @Override
    public GetLogResponse collectLogRecords(LogRecords.Builder logRecords) {
      return GetLogResponse.newBuilder().setLogRecords(logRecords).build();
    }
  }
}
