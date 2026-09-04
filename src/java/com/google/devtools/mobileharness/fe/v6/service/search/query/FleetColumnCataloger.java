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

import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.DimensionCatalogStore;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Facade dispatcher for the column selector catalog across search entities (device vs host).
 *
 * <p>Delegates to {@link DeviceColumnCataloger} for device search and {@link HostColumnCataloger}
 * for host search, keeping the entity-specific pipelines, section structures, and dependencies
 * decoupled.
 */
@Singleton
public final class FleetColumnCataloger {

  private final DeviceColumnCataloger deviceCataloger;
  private final HostColumnCataloger hostCataloger;

  @Inject
  FleetColumnCataloger(DeviceColumnCataloger deviceCataloger, HostColumnCataloger hostCataloger) {
    this.deviceCataloger = deviceCataloger;
    this.hostCataloger = hostCataloger;
  }

  public FleetColumnCataloger(DimensionCatalogStore dimensionCatalogStore) {
    this(new DeviceColumnCataloger(dimensionCatalogStore), new HostColumnCataloger());
  }

  public FleetColumnCataloger() {
    this(new DeviceColumnCataloger(), new HostColumnCataloger());
  }

  /** Returns the ordered column catalog for the requested entity and dialog state. */
  public FleetColumnCatalogResponse getColumnCatalog(
      SearchCorpus corpus, FleetColumnCatalogRequest request) {
    if (corpus.entity() == SearchEntity.SEARCH_ENTITY_HOST) {
      return hostCataloger.getColumnCatalog((HostCorpus) corpus, request);
    }
    return deviceCataloger.getColumnCatalog((DeviceCorpus) corpus, request);
  }
}
