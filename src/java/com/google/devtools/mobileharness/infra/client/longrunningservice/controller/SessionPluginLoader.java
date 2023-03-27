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

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.api.error.DeviceInfraException;
import com.google.devtools.deviceinfra.shared.util.reflection.ReflectionUtil;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginConfigs;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginExecutionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLabel;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLoadingConfig;
import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import javax.inject.Inject;

/** Loader for loading session plugins. */
public class SessionPluginLoader {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ReflectionUtil reflectionUtil;

  @Inject
  SessionPluginLoader(ReflectionUtil reflectionUtil) {
    this.reflectionUtil = reflectionUtil;
  }

  public ImmutableMap<SessionPluginLabel, Object> loadSessionPlugins(
      SessionPluginConfigs sessionPluginConfigs, SessionInfo sessionInfo)
      throws MobileHarnessException {
    ImmutableMap.Builder<SessionPluginLabel, Object> sessionPlugins = ImmutableMap.builder();
    for (SessionPluginConfig sessionPluginConfig :
        sessionPluginConfigs.getSessionPluginConfigList()) {
      SessionPluginLabel sessionPluginLabel = getSessionPluginLabel(sessionPluginConfig);
      logger.atInfo().log(
          "Loading session plugin: label=[%s], config=[%s]",
          shortDebugString(sessionPluginLabel), shortDebugString(sessionPluginConfig));

      // Loads plugin class.
      Class<?> sessionPluginClass = loadSessionPluginClass(sessionPluginConfig.getLoadingConfig());

      // Creates plugin instance.
      Object sessionPlugin =
          createSessionPlugin(
              sessionPluginClass,
              sessionInfo,
              sessionPluginConfig.getExecutionConfig(),
              sessionPluginLabel);
      sessionPlugins.put(sessionPluginLabel, sessionPlugin);
    }
    try {
      return sessionPlugins.buildOrThrow();
    } catch (IllegalArgumentException e) {
      throw new MobileHarnessException(
          InfraErrorId.OLCS_DUPLICATED_SESSION_PLUGIN_LABEL,
          "Duplicated session plugin label. If you want to specify more than one session plugins"
              + " whose class names are the same, you should specify [explicit_label] for those"
              + " plugins.",
          e);
    }
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
    } catch (DeviceInfraException e) {
      throw new MobileHarnessException(
          InfraErrorId.OLCS_LOAD_BUILTIN_SESSION_PLUGIN_CLASS_ERROR,
          String.format("Failed to load builtin session plugin class [%s]", pluginClassName),
          e);
    }
  }

  private Object createSessionPlugin(
      Class<?> sessionPluginClass,
      SessionInfo sessionInfo,
      SessionPluginExecutionConfig executionConfig,
      SessionPluginLabel label)
      throws MobileHarnessException {
    try {
      return Guice.createInjector(new SessionPluginModule(sessionInfo, executionConfig, label))
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
    private final SessionPluginExecutionConfig executionConfig;
    private final SessionPluginLabel label;

    private SessionPluginModule(
        SessionInfo sessionInfo,
        SessionPluginExecutionConfig executionConfig,
        SessionPluginLabel label) {
      this.sessionInfo = sessionInfo;
      this.executionConfig = executionConfig;
      this.label = label;
    }

    @Provides
    SessionInfo provideSessionInfo() {
      return sessionInfo;
    }

    @Provides
    SessionPluginExecutionConfig provideExecutionConfig() {
      return executionConfig;
    }

    @Provides
    SessionPluginLabel provideLabel() {
      return label;
    }
  }
}
