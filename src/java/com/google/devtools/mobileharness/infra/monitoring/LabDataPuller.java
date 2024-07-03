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

package com.google.devtools.mobileharness.infra.monitoring;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Filter;
import com.google.devtools.mobileharness.shared.labinfo.LabInfoProvider;

final class LabDataPuller implements DataPuller<LabData> {

  private final LabInfoProvider labInfoProvider;

  public static LabDataPuller create(LabInfoProvider labInfoProvider) {
    return new LabDataPuller(labInfoProvider);
  }

  LabDataPuller(LabInfoProvider labInfoProvider) {
    this.labInfoProvider = labInfoProvider;
  }

  @Override
  public void setUp() {}

  @Override
  public ImmutableList<LabData> pull() {
    return ImmutableList.copyOf(
        labInfoProvider.getLabInfos(Filter.getDefaultInstance()).getLabDataList());
  }

  @Override
  public void tearDown() {}
}
