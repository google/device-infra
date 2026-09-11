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

package com.google.devtools.mobileharness.shared.labinfo;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Filter;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;

/** Provider for providing {@link LabView}. */
public interface LabInfoProvider {

  /** Gets information of lab(s). */
  LabView getLabInfos(Filter filter) throws MobileHarnessException;

  /**
   * Gets information of lab(s), applying projection push-down if a {@link Mask} is provided.
   *
   * <p>Providers that support projection push-down should override this method to avoid
   * constructing intermediate unmasked protobuf subtrees on the heap.
   */
  default LabView getLabInfos(Filter filter, Mask mask) throws MobileHarnessException {
    return getLabInfos(filter);
  }

  /** Returns true if this provider applies {@link Mask} push-down inside {@link #getLabInfos}. */
  default boolean supportsProjectionPushDown() {
    return false;
  }
}
