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

package com.google.devtools.mobileharness.infra.controller.device.config;

import com.google.common.util.concurrent.AbstractIdleService;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.inject.assistedinject.Assisted;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil;
import java.util.Set;
import javax.inject.Inject;

/**
 * Service for configuring ApiConfig at runtime.
 *
 * <p>ApiConfig is managed outside of Guice, so this uses a Service to inject it and add its
 * registered observers and initialize it.
 */
final class ApiConfigService extends AbstractIdleService {
  private final ApiConfig apiConfig;
  private final Set<ApiConfigListener> listeners;
  private final NetUtil netUtil;
  private final SystemUtil systemUtil;
  private final boolean hasDefaultSynced;
  private final boolean isDefaultPublic;

  @Inject
  ApiConfigService(
      ApiConfig apiConfig,
      Set<ApiConfigListener> listeners,
      NetUtil netUtil,
      SystemUtil systemUtil,
      @Assisted("hasDefaultSynced") boolean hasDefaultSynced,
      @Assisted("isDefaultPublic") boolean isDefaultPublic) {
    this.apiConfig = apiConfig;
    this.listeners = listeners;
    this.netUtil = netUtil;
    this.systemUtil = systemUtil;
    this.hasDefaultSynced = hasDefaultSynced;
    this.isDefaultPublic = isDefaultPublic;
  }

  interface Factory {
    ApiConfigService create(
        @Assisted("hasDefaultSynced") boolean hasDefaultSynced,
        @Assisted("isDefaultPublic") boolean isDefaultPublic);
  }

  @Override
  protected void startUp() throws MobileHarnessException {
    for (ApiConfigListener listener : listeners) {
      apiConfig.addListener(listener);
    }
    apiConfig.initialize(
        /* isDefaultPublic= */ isDefaultPublic || isDefaultPublicForEnvironment(),
        /* isDefaultSynced= */ hasDefaultSynced,
        netUtil.getLocalHostName());
  }

  @Override
  protected void shutDown() {}

  private boolean isDefaultPublicForEnvironment() {
    return true;
  }
}
