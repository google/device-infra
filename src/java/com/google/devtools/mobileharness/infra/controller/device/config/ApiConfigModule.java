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

import com.google.devtools.mobileharness.shared.util.concurrent.ServiceModule;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.Multibinder;

/** Module for providing {@link ApiConfig}. */
public final class ApiConfigModule extends AbstractModule {
  private final boolean hasDefaultSynced;
  private final boolean isDefaultPublic;

  /** Constructs an {@link ApiConfigModule} with the given {@code hasDefaultSynced} value. */
  public ApiConfigModule(boolean hasDefaultSynced) {
    this(hasDefaultSynced, /* isDefaultPublic= */ false);
  }

  /**
   * Constructs an {@link ApiConfigModule} with the given default synced and default public values.
   */
  public ApiConfigModule(boolean hasDefaultSynced, boolean isDefaultPublic) {
    this.hasDefaultSynced = hasDefaultSynced;
    this.isDefaultPublic = isDefaultPublic;
  }

  @Override
  protected void configure() {
    // This is needed because ApiConfig is statically initialized and created by its consumers
    // without the use of Guice. Static injection is required to inject the dependencies of
    // ApiConfigV5 without refactoring all of its consumers.
    requestStaticInjection(ApiConfigV5.class);
    install(new FactoryModuleBuilder().build(ApiConfigService.Factory.class));
    install(ServiceModule.forService(ApiConfigService.class));
    Multibinder.newSetBinder(binder(), ApiConfigListener.class);
  }

  @Provides
  ApiConfigService provideApiConfigService(ApiConfigService.Factory configServiceFactory) {
    return configServiceFactory.create(hasDefaultSynced, isDefaultPublic);
  }

  @Provides
  ApiConfig provideApiConfig() {
    return ApiConfig.getInstance();
  }
}
