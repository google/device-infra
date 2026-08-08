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

package com.google.devtools.mobileharness.fe.v6.service.search.refresh;

import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.AtsOneFleetDataSource;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.FleetDataSource;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;

/**
 * Guice bindings for the OSS (ats-one) fleet search data pipeline.
 *
 * <p>Binds the per-fleet {@link FleetDataSource} map that {@link FleetDataRefresher} iterates. The
 * OSS build serves a single local ATS controller, so it binds only the ats-one source under {@link
 * Fleet#FLEET_SELF}. The internal build installs its own module that adds the 1p and ats-all
 * sources.
 *
 * <p>Installing this module wires the pipeline but does not start it; the server starts the
 * refresher at activation.
 */
public final class FleetSearchDataModule extends AbstractModule {

  @Override
  protected void configure() {
    MapBinder<Fleet, FleetDataSource> sources =
        MapBinder.newMapBinder(binder(), Fleet.class, FleetDataSource.class);
    sources.addBinding(Fleet.FLEET_SELF).to(AtsOneFleetDataSource.class);
  }
}
