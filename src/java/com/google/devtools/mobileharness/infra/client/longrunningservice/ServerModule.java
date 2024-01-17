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
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.api.Annotations.GlobalInternalEventBus;
import com.google.devtools.mobileharness.infra.client.api.ClientApi;
import com.google.devtools.mobileharness.infra.client.api.ClientApiModule;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.api.mode.ExecMode;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.ServerStartTime;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.ControllerModule;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogManager.LogRecordsCollector;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecords;
import com.google.devtools.mobileharness.infra.controller.test.util.SubscriberExceptionLoggingHandler;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.reflection.ReflectionUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import javax.inject.Singleton;

/** Module for OLC server. */
class ServerModule extends AbstractModule {

  private static final ReflectionUtil REFLECTION_UTIL = new ReflectionUtil();

  private static final String LOCAL_MODE_CLASS_NAME =
      "com.google.devtools.mobileharness.infra.client.api.mode.local.LocalMode";
  private static final String ATS_MODE_CLASS_NAME =
      "com.google.devtools.mobileharness.infra.client.api.mode.ats.AtsMode";
  private static final String ATS_MODE_MODULE_CLASS_NAME =
      "com.google.devtools.mobileharness.infra.client.api.mode.ats.AtsModeModule";

  private final Instant serverStartTime;
  private final boolean isAtsMode;

  ServerModule(boolean isAtsMode, Instant serverStartTime) {
    this.serverStartTime = serverStartTime;
    this.isAtsMode = isAtsMode;
  }

  @Override
  protected void configure() {
    install(new ControllerModule());
    install(new ClientApiModule());
    if (isAtsMode) {
      installByClassName(ATS_MODE_MODULE_CLASS_NAME);
    }

    bind(ClientApi.class).in(Scopes.SINGLETON);
    bind(ExecMode.class)
        .to(isAtsMode ? loadExecMode(ATS_MODE_CLASS_NAME) : loadExecMode(LOCAL_MODE_CLASS_NAME))
        .in(Scopes.SINGLETON);
  }

  @Provides
  @ServerStartTime
  Instant provideServerStartTime() {
    return serverStartTime;
  }

  @Provides
  @Singleton
  DeviceQuerier provideDeviceQuerier(ExecMode execMode) {
    return execMode.createDeviceQuerier();
  }

  @Provides
  @Singleton
  ListeningExecutorService provideThreadPool() {
    return ThreadPools.createStandardThreadPool("main-thread");
  }

  @Provides
  @Singleton
  ListeningScheduledExecutorService provideScheduledThreadPool() {
    return ThreadPools.createStandardScheduledThreadPool(
        "main-scheduled-thread", /* corePoolSize= */ 10);
  }

  @Provides
  ExecutorService provideExecutorService(ListeningExecutorService listeningExecutorService) {
    return listeningExecutorService;
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

  private void installByClassName(@SuppressWarnings("SameParameterValue") String className) {
    try {
      install(
          REFLECTION_UTIL
              .loadClass(className, Module.class, getClass().getClassLoader())
              .getConstructor()
              .newInstance());
    } catch (MobileHarnessException | ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Class<? extends ExecMode> loadExecMode(String className) {
    try {
      return REFLECTION_UTIL.loadClass(
          className, ExecMode.class, ServerModule.class.getClassLoader());
    } catch (MobileHarnessException | ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }
}
