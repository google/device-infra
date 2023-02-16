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
import com.google.devtools.deviceinfra.api.error.DeviceInfraException;
import com.google.devtools.deviceinfra.shared.util.reflection.ReflectionUtil;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.BuiltinSessionPlugin;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginConfig;
import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import java.util.List;
import javax.inject.Inject;

/** Loader for loading session plugins. */
public class SessionPluginLoader {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ReflectionUtil reflectionUtil;

  @Inject
  SessionPluginLoader(ReflectionUtil reflectionUtil) {
    this.reflectionUtil = reflectionUtil;
  }

  public List<Object> loadSessionPlugins(
      SessionPluginConfig sessionPluginConfig, SessionInfo sessionInfo)
      throws MobileHarnessException {
    ImmutableList.Builder<Object> sessionPlugins = ImmutableList.builder();

    // Loads builtin session plugins.
    for (BuiltinSessionPlugin builtinPlugin : sessionPluginConfig.getBuiltinPluginList()) {
      logger.atInfo().log("Loading builtin session plugin: %s", shortDebugString(builtinPlugin));
      Class<?> builtinSessionPluginClass =
          loadBuiltinSessionPluginClass(builtinPlugin.getLoadingConfig().getPluginClassName());
      Object builtinSessionPlugin = createSessionPlugin(builtinSessionPluginClass, sessionInfo);
      sessionPlugins.add(builtinSessionPlugin);
    }
    return sessionPlugins.build();
  }

  private Class<?> loadBuiltinSessionPluginClass(String builtinSessionPluginClassName)
      throws MobileHarnessException {
    try {
      return reflectionUtil.loadClass(
          builtinSessionPluginClassName, Object.class, getClass().getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new MobileHarnessException(
          InfraErrorId.OLCS_BUILTIN_SESSION_PLUGIN_NOT_FOUND,
          String.format("No builtin session plugin class [%s]", builtinSessionPluginClassName),
          e);
    } catch (DeviceInfraException e) {
      throw new MobileHarnessException(
          InfraErrorId.OLCS_LOAD_BUILTIN_SESSION_PLUGIN_CLASS_ERROR,
          String.format(
              "Failed to load builtin session plugin class [%s]", builtinSessionPluginClassName),
          e);
    }
  }

  private Object createSessionPlugin(Class<?> sessionPluginClass, SessionInfo sessionInfo)
      throws MobileHarnessException {
    try {
      return Guice.createInjector(new SessionPluginModule(sessionInfo))
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

    private SessionPluginModule(SessionInfo sessionInfo) {
      this.sessionInfo = sessionInfo;
    }

    @Provides
    SessionInfo provideSessionInfo() {
      return sessionInfo;
    }
  }
}
