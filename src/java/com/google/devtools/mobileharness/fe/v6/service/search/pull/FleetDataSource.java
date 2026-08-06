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

package com.google.devtools.mobileharness.fe.v6.service.search.pull;

import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetRawData;

/**
 * A source of raw search data for a single fleet.
 *
 * <p>There is exactly one source per {@link Fleet}. Each build binds the sources its deployment can
 * reach: the OSS build binds the ats-one source under {@link Fleet#FLEET_SELF}; the internal build
 * binds the 1p source under {@link Fleet#FLEET_SELF} and the aggregated ATS source under {@link
 * Fleet#FLEET_ATS}. {@link #pull()} produces the {@link FleetRawData} that {@code
 * FleetIndexBuilder} turns into a snapshot.
 *
 * <p>{@link #pull()} blocks until every backend it consults responds. The refresh scheduler runs it
 * off the serving path, so blocking here is intentional and keeps each source implementation
 * simple.
 */
public interface FleetDataSource {

  /** The fleet this source produces data for. */
  Fleet fleet();

  /**
   * Pulls one full set of raw data for this fleet.
   *
   * @throws Exception if a backend this source consults fails; the refresher logs it and keeps the
   *     previously published snapshot for this fleet
   */
  FleetRawData pull() throws Exception;
}
