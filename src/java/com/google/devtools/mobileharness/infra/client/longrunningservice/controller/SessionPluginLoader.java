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

import static com.google.protobuf.TextFormat.shortDebugString;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.ServerStartTime;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionDetailHolder;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionPlugin;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLabel;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLoadingConfig;
import com.google.devtools.mobileharness.shared.util.event.EventBusBackend;
import com.google.devtools.mobileharness.shared.util.event.EventBusBackend.SubscriberMethodSearchResult;
import com.google.devtools.mobileharness.shared.util.reflection.ReflectionUtil;
import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.ProvisionException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;

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

  public ImmutableList<SessionPlugin> loadSessionPlugins(SessionDetailHolder sessionDetailHolder)
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
      Class<?> sessionPluginClass = loadSessionPluginClass(sessionPluginConfig.getLoadingConfig());

      // Creates SessionInfo for the plugin.
      SessionInfo sessionInfo =
          new SessionInfo(
              sessionDetailHolder, sessionPluginLabel, sessionPluginConfig.getExecutionConfig());

      // Creates plugin instance.
      Object sessionPlugin = createSessionPlugin(sessionPluginClass, sessionInfo);

      // Searches subscriber methods.
      SubscriberMethodSearchResult subscriberMethodSearchResult =
          eventBusBackend.searchSubscriberMethods(sessionPlugin);

      sessionPlugins.add(
          SessionPlugin.of(sessionInfo, sessionPlugin, subscriberMethodSearchResult));
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

  private Class<?> loadSessionPluginClass(SessionPluginLoadingConfig loadingConfig)
      throws MobileHarnessException {
    // Only supports builtin session plugin now.
    String pluginClassName = loadingConfig.getPluginClassName();
    try {
      return reflectionUtil.loadClass(pluginClassName, Object.class, getClass().getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new MobileHarnessException(
          InfraErrorId.OLCS_BUILTIN_SESSION_PLUGIN_NOT_FOUND,
          String.format("No builtin session plugin class [%s]", pluginClassName),
          e);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          InfraErrorId.OLCS_LOAD_BUILTIN_SESSION_PLUGIN_CLASS_ERROR,
          String.format("Failed to load builtin session plugin class [%s]", pluginClassName),
          e);
    }
  }

  private Object createSessionPlugin(Class<?> sessionPluginClass, SessionInfo sessionInfo)
      throws MobileHarnessException {
    try {
      return Guice.createInjector(
              new SessionPluginModule(sessionInfo, deviceQuerier, serverStartTime))
          .getInstance(sessionPluginClass);
    } catch (CreationException | ProvisionException e) {
      throw new MobileHarnessException(
          InfraErrorId.OLCS_CREATE_SESSION_PLUGIN_ERROR,
          String.format("Failed to create session plugin [%s]", sessionPluginClass.getName()),
          e);
    }
  }

  private static class SessionPluginModule extends AbstractModule {

    private final SessionInfo sessionInfo;
    private final DeviceQuerier deviceQuerier;
    private final Instant serverStartTime;

    private SessionPluginModule(
        SessionInfo sessionInfo, DeviceQuerier deviceQuerier, Instant serverStartTime) {
      this.sessionInfo = sessionInfo;
      this.deviceQuerier = deviceQuerier;
      this.serverStartTime = serverStartTime;
    }

    @Override
    protected void configure() {
      bind(SessionInfo.class).toInstance(sessionInfo);
      bind(DeviceQuerier.class).toInstance(deviceQuerier);
      bind(Instant.class).annotatedWith(ServerStartTime.class).toInstance(serverStartTime);
    }
  }
}
