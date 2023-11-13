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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.inject.Qualifier;

/** Annotations for {@code ClientApi}. */
public class Annotations {

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface EnvThreadPool {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface JobThreadPool {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface ExtraGlobalInternalPlugins {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface ExtraJobInternalPlugins {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface ShutdownJobThreadWhenShutdownProcess {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  public @interface GlobalInternalEventBus {}

  private Annotations() {}
}
