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

import com.google.devtools.mobileharness.infra.controller.device.DeviceIdManager;
import com.google.devtools.mobileharness.shared.util.concurrent.ServiceModule;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import java.time.Clock;

/** Module for providing {@link ApiConfig}. */
public final class ApiConfigModule extends AbstractModule {
  private final boolean hasDefaultSynced;
  private final BasicDeviceConfigMerger basicDeviceConfigMerger;

  /** Constructs an {@link ApiConfigModule} with the given {@code hasDefaultSynced} value. */
  public ApiConfigModule(boolean hasDefaultSynced) {
    this(hasDefaultSynced, new BasicDeviceConfigMerger() {});
  }

  public ApiConfigModule(
      boolean hasDefaultSynced, BasicDeviceConfigMerger basicDeviceConfigMerger) {
    this.hasDefaultSynced = hasDefaultSynced;
    this.basicDeviceConfigMerger = basicDeviceConfigMerger;
  }

  @Override
  protected void configure() {
    install(ServiceModule.forService(ApiConfigService.class));
    Multibinder.newSetBinder(binder(), ApiConfigListener.class);
    bind(Boolean.class).annotatedWith(HasDefaultSynced.class).toInstance(hasDefaultSynced);
  }

  @Provides
  ApiConfig provideApiConfig() {
    return new ApiConfigV5(
        DeviceIdManager.getInstance(),
        new SystemUtil(),
        Clock.systemUTC(),
        basicDeviceConfigMerger);
  }
}
