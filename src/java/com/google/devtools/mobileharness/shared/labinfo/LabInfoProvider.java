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

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceError;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Filter;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.shared.util.filter.CompiledDeviceInfoMask;
import com.google.devtools.mobileharness.shared.util.filter.CompiledLabInfoMask;
import com.google.devtools.mobileharness.shared.util.filter.MaskUtils;

/** Provider for providing {@link LabView}. */
public interface LabInfoProvider {

  /** Gets information of lab(s). */
  LabView getLabInfos(Filter filter) throws MobileHarnessException;

  /**
   * Gets information of lab(s) containing only what {@code labInfoMask} and {@code deviceInfoMask}
   * keep. {@code lab_total_count} and {@code device_total_count} are unaffected by the masks.
   *
   * <p>The default builds the full view with {@link #getLabInfos(Filter)} and trims it. Providers
   * override this to build only the requested parts in the first place, which avoids the full view
   * ever existing on the heap; the result must be the same either way.
   */
  default LabView getLabInfos(
      Filter filter, CompiledLabInfoMask labInfoMask, CompiledDeviceInfoMask deviceInfoMask)
      throws MobileHarnessException {
    return MaskUtils.trimLabView(getLabInfos(filter), labInfoMask, deviceInfoMask);
  }

  /**
   * Gets the device errors reported from the lab for the device {@code deviceId} on the lab host
   * {@code hostName}. Both are required, and only that host is looked up.
   *
   * <p>The default returns an empty list, for providers that do not support it yet.
   *
   * @throws MobileHarnessException if the lab or the device is not found
   */
  // TODO: b/585438621 - Implement getDeviceErrors in OSS providers (LocalLabInfoProvider and
  // RemoteDeviceManager).
  default ImmutableList<DeviceError> getDeviceErrors(String deviceId, String hostName)
      throws MobileHarnessException {
    return ImmutableList.of();
  }
}
