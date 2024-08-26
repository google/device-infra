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

package com.google.devtools.mobileharness.service.moss.util.slg;

import com.google.devtools.mobileharness.service.moss.proto.Slg.PropertiesProto;
import com.google.wireless.qa.mobileharness.shared.model.job.out.JobOutInternalFactory;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;

/**
 * Utility class to help convert a {@link Properties} to a {@link PropertiesProto} in forward and
 * backward.
 */
final class PropertiesConverter {

  private PropertiesConverter() {}

  /** Gets a {@link Properties} by the given {@link Timing} and {@link PropertiesProto}. */
  static Properties fromProto(Timing timing, PropertiesProto propertiesProto) {
    return JobOutInternalFactory.createProperties(timing, propertiesProto);
  }

  /** Gets a {@link PropertiesProto} by the given {@link Properties}. */
  static PropertiesProto toProto(Properties properties) {
    return PropertiesProto.newBuilder().putAllProperties(properties.getAll()).build();
  }
}
