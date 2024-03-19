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

package com.google.devtools.mobileharness.infra.client.longrunningservice.controller;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.protobuf.TextFormat.shortDebugString;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.ServerStartTime;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.SessionGenDir;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.SessionTempDir;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.SessionEnvironmentPreparer.SessionEnvironment;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionDetailHolder;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionPlugin;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLabel;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLoadingConfig;
import com.google.devtools.mobileharness.infra.controller.plugin.loader.PluginInstantiator;
import com.google.devtools.mobileharness.shared.constant.closeable.NonThrowingAutoCloseable;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.event.EventBusBackend;
import com.google.devtools.mobileharness.shared.util.event.EventBusBackend.Subscriber;
import com.google.devtools.mobileharness.shared.util.reflection.ReflectionUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Loader for loading session plugins. */
public class SessionPluginLoader {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ReflectionUtil reflectionUtil;
  private final EventBusBackend eventBusBackend;
  private final DeviceQuerier deviceQuerier;
  private final Instant serverStartTime;

  @Inject
  SessionPluginLoader(
      ReflectionUtil reflectionUtil,
      EventBusBackend eventBusBackend,
      DeviceQuerier deviceQuerier,
      @ServerStartTime Instant serverStartTime) {
    this.reflectionUtil = reflectionUtil;
    this.eventBusBackend = eventBusBackend;
    this.deviceQuerier = deviceQuerier;
    this.serverStartTime = serverStartTime;
  }

  public ImmutableList<SessionPlugin> loadSessionPlugins(
      SessionDetailHolder sessionDetailHolder, SessionEnvironment sessionEnvironment)
      throws MobileHarnessException {
    ImmutableList.Builder<SessionPlugin> sessionPlugins = ImmutableList.builder();

    Set<SessionPluginLabel> existingLabels = new HashSet<>();

    for (SessionPluginConfig sessionPluginConfig :
        sessionDetailHolder
            .getSessionConfig()
            .getSessionPluginConfigs()
            .getSessionPluginConfigList()) {
      SessionPluginLabel sessionPluginLabel = getSessionPluginLabel(sessionPluginConfig);
      logger.atInfo().log(
          "Loading session plugin: label=[%s], config=[%s]",
          shortDebugString(sessionPluginLabel), shortDebugString(sessionPluginConfig));

      // Checks plugin label.
      if (existingLabels.contains(sessionPluginLabel)) {
        throw new MobileHarnessException(
            InfraErrorId.OLCS_DUPLICATED_SESSION_PLUGIN_LABEL,
            String.format(
                "Duplicated session plugin label [%s]. If you want to specify more than one session"
                    + " plugins whose class names are the same, you should specify [explicit_label]"
                    + " for those plugins.",
                shortDebugString(sessionPluginLabel)));
      } else {
        existingLabels.add(sessionPluginLabel);
      }

      // Loads plugin class.
      SessionPluginClasses sessionPluginClasses =
          loadSessionPluginClasses(sessionPluginConfig.getLoadingConfig());

      // Creates SessionInfo for the plugin.
      SessionInfo sessionInfo =
          new SessionInfo(
              sessionDetailHolder, sessionPluginLabel, sessionPluginConfig.getExecutionConfig());

      // Creates default module.
      CloseableResources closeableResources = new CloseableResources();
      SessionPluginDefaultModule defaultModule =
          new SessionPluginDefaultModule(
              sessionInfo, deviceQuerier, serverStartTime, sessionEnvironment, closeableResources);

      // Creates session plugin.
      Object sessionPlugin =
          PluginInstantiator.instantiatePlugin(
              sessionPluginClasses.sessionPluginClass(),
              sessionPluginClasses.sessionPluginModuleClass().stream().collect(toImmutableList()),
              ImmutableList.of(defaultModule));

      // Searches subscriber methods.
      Subscriber subscriber = eventBusBackend.searchSubscriberMethods(sessionPlugin);

      sessionPlugins.add(SessionPlugin.of(sessionInfo, subscriber, closeableResources));
    }
    return sessionPlugins.build();
  }

  private SessionPluginLabel getSessionPluginLabel(SessionPluginConfig sessionPluginConfig) {
    if (sessionPluginConfig.hasExplicitLabel()) {
      return sessionPluginConfig.getExplicitLabel();
    } else {
      return SessionPluginLabel.newBuilder()
          .setLabel(sessionPluginConfig.getLoadingConfig().getPluginClassName())
          .build();
    }
  }

  private SessionPluginClasses loadSessionPluginClasses(SessionPluginLoadingConfig loadingConfig)
      throws MobileHarnessException {
    // Only supports builtin session plugin now.
    try {
      // Loads plugin class.
      String pluginClassName = loadingConfig.getPluginClassName();
      Class<?> pluginClass =
          reflectionUtil.loadClass(pluginClassName, Object.class, getClass().getClassLoader());

      // Loads plugin module class.
      String pluginModuleClassName = loadingConfig.getPluginModuleClassName();
      Class<? extends Module> pluginModuleClass;
      if (pluginModuleClassName.isEmpty()) {
        pluginModuleClass = null;
      } else {
        pluginModuleClass =
            reflectionUtil.loadClass(
                pluginModuleClassName, Module.class, getClass().getClassLoader());
      }

      return SessionPluginClasses.of(pluginClass, pluginModuleClass);
    } catch (ClassNotFoundException e) {
      throw new MobileHarnessException(
          InfraErrorId.OLCS_BUILTIN_SESSION_PLUGIN_NOT_FOUND,
          String.format(
              "Builtin session plugin or module class not found, loading_config=[%s]",
              shortDebugString(loadingConfig)),
          e);
    }
  }

  private static class SessionPluginDefaultModule extends AbstractModule {

    private final SessionInfo sessionInfo;
    private final DeviceQuerier deviceQuerier;
    private final Instant serverStartTime;
    private final SessionEnvironment sessionEnvironment;
    private final CloseableResources closeableResources;

    private SessionPluginDefaultModule(
        SessionInfo sessionInfo,
        DeviceQuerier deviceQuerier,
        Instant serverStartTime,
        SessionEnvironment sessionEnvironment,
        CloseableResources closeableResources) {
      this.sessionInfo = sessionInfo;
      this.deviceQuerier = deviceQuerier;
      this.serverStartTime = serverStartTime;
      this.sessionEnvironment = sessionEnvironment;
      this.closeableResources = closeableResources;
    }

    @Override
    protected void configure() {
      bind(SessionInfo.class).toInstance(sessionInfo);
      bind(DeviceQuerier.class).toInstance(deviceQuerier);
      bind(Instant.class).annotatedWith(ServerStartTime.class).toInstance(serverStartTime);
      bind(Clock.class).toInstance(Clock.systemUTC());
      bind(Path.class)
          .annotatedWith(SessionGenDir.class)
          .toInstance(sessionEnvironment.sessionGenDir());
      bind(Path.class)
          .annotatedWith(SessionTempDir.class)
          .toInstance(sessionEnvironment.sessionTempDir());
    }

    @Provides
    @Singleton
    ListeningExecutorService provideListeningExecutorService() {
      ListeningExecutorService threadPool =
          ThreadPools.createStandardThreadPool(
              String.format("session-plugin-%s-thread-pool", sessionInfo.getSessionPluginLabel()));
      closeableResources.add(threadPool::shutdownNow);
      return threadPool;
    }
  }

  private static class CloseableResources implements NonThrowingAutoCloseable {

    @GuardedBy("itself")
    private final List<NonThrowingAutoCloseable> closeableResources = new ArrayList<>();

    private void add(NonThrowingAutoCloseable closeableResource) {
      synchronized (closeableResources) {
        closeableResources.add(closeableResource);
      }
    }

    @Override
    public void close() {
      synchronized (closeableResources) {
        closeableResources.forEach(NonThrowingAutoCloseable::close);
      }
    }
  }

  @AutoValue
  abstract static class SessionPluginClasses {

    abstract Class<?> sessionPluginClass();

    abstract Optional<Class<? extends Module>> sessionPluginModuleClass();

    private static SessionPluginClasses of(
        Class<?> sessionPluginClass, @Nullable Class<? extends Module> sessionPluginModuleClass) {
      return new AutoValue_SessionPluginLoader_SessionPluginClasses(
          sessionPluginClass, Optional.ofNullable(sessionPluginModuleClass));
    }
  }
}
