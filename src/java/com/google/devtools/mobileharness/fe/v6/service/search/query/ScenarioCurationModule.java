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

package com.google.devtools.mobileharness.fe.v6.service.search.query;

import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;

/**
 * Guice bindings for the OSS (ats) fleet scenario curation.
 *
 * <p>Binds the per-fleet {@link ScenarioCuration} map that promoted-key, suggester, and column
 * consumers read. The OSS build serves a single local ATS controller, so it binds only the ats
 * curation under {@link Fleet#FLEET_SELF}. The internal build installs its own module that adds the
 * internal ({@code FLEET_SELF}) and partner-ats ({@code FLEET_ATS}) curations.
 *
 * <p>This module is inert: it is not installed anywhere yet. The server installs it at activation,
 * alongside the promoted-key and suggester consumers that will read the map.
 */
public final class ScenarioCurationModule extends AbstractModule {

  @Override
  protected void configure() {
    MapBinder<Fleet, ScenarioCuration> curations =
        MapBinder.newMapBinder(binder(), Fleet.class, ScenarioCuration.class);
    curations.addBinding(Fleet.FLEET_SELF).to(AtsCuration.class);
  }
}
