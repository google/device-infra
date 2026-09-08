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

package com.google.devtools.mobileharness.fe.v6.service.search;

import com.google.devtools.mobileharness.fe.v6.service.search.pull.LabInfoFleetPuller;
import com.google.devtools.mobileharness.fe.v6.service.search.query.ScenarioCurationModule;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.FleetSearchDataModule;
import com.google.devtools.mobileharness.fe.v6.service.search.summary.GlobalSummaryProvider;
import com.google.devtools.mobileharness.fe.v6.service.search.summary.NoOpGlobalSummaryProvider;
import com.google.devtools.mobileharness.fe.v6.service.search.tjs.NoOpTjsSearchLogic;
import com.google.devtools.mobileharness.fe.v6.service.search.tjs.TjsSearchLogic;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/**
 * Guice bindings for the OSS fleet search service.
 *
 * <p>Binds {@link SearchServiceLogic} to its in-memory implementation, binds {@link TjsSearchLogic}
 * to its no-op implementation, and installs the OSS fleet data pipeline ({@link
 * FleetSearchDataModule}, the {@code FLEET_SELF} to ats-one data source) and the OSS curation
 * ({@link ScenarioCurationModule}, the {@code FLEET_SELF} to ats-one curation).
 *
 * <p>The refresh pipeline is correct only if a single {@code FleetSnapshotStore} instance is shared
 * between the refresher that writes snapshots and the query classes that read them. {@code
 * FleetSnapshotStore} and {@code FleetDataRefresher} carry {@link Singleton} on the class, so Guice
 * serves one instance of each. {@link LabInfoFleetPuller} carries no scope annotation, so it is
 * scoped here for symmetry with the rest of the pipeline.
 *
 * <p>Installing this module wires the service but does not start it. The refresher is scheduled by
 * the server at activation, not here.
 */
public final class SearchServiceModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(SearchServiceLogic.class).to(SearchServiceLogicImpl.class);
    bind(TjsSearchLogic.class).to(NoOpTjsSearchLogic.class);
    bind(GlobalSummaryProvider.class).to(NoOpGlobalSummaryProvider.class);
    bind(LabInfoFleetPuller.class).in(Singleton.class);
    install(new FleetSearchDataModule());
    install(new ScenarioCurationModule());
  }
}
