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

package com.google.devtools.mobileharness.infra.ats.common;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteHelper.DeviceInfo;
import java.util.Optional;

/** Helper interface for operating on local devices. */
public interface LocalDeviceUtil {

  /** Gets a list of local available devices based on the session request info. */
  ImmutableSet<DeviceDetails> getLocalAvailableDevices(SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException, InterruptedException;

  /** Gets device info from a local device. */
  Optional<DeviceInfo> getDeviceInfoFromLocal(SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException, InterruptedException;

  /** No-op implementation of {@link LocalDeviceUtil}. */
  class NoOp implements LocalDeviceUtil {

    @Override
    public ImmutableSet<DeviceDetails> getLocalAvailableDevices(
        SessionRequestInfo sessionRequestInfo) {
      throw new UnsupportedOperationException("Local device operations are not supported.");
    }

    @Override
    public Optional<DeviceInfo> getDeviceInfoFromLocal(SessionRequestInfo sessionRequestInfo) {
      throw new UnsupportedOperationException("Local device operations are not supported.");
    }
  }
}
