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

package com.google.wireless.qa.mobileharness.shared.api.device;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.container.annotation.ProcessIncompatible;
import java.lang.reflect.Constructor;

/** Simple factory for creating {@link Device} instances. */
@ProcessIncompatible
public class DeviceFactory {
  /**
   * Creates a new {@link Device} instance accroding to the given {@link Device} class type.
   *
   * @param deviceClass {@link Device} class type
   * @param deviceControlId device control ID
   * @return a new {@link Device} instance with the given ID
   * @throws MobileHarnessException if fails to create a new {@link Device} instance
   */
  public Device createDevice(Class<? extends Device> deviceClass, String deviceControlId)
      throws MobileHarnessException {
    String deviceClassName = deviceClass.getSimpleName();
    // Gets the constructor.
    Constructor<? extends Device> deviceConstructor;
    String errMsg = "Cannot create " + deviceClassName + " for " + deviceControlId;
    try {
      deviceConstructor = deviceClass.getConstructor(String.class);
    } catch (NoSuchMethodException e) {
      throw new MobileHarnessException(
          BasicErrorId.REFLECTION_CREATE_DEVICE_CONSTRUCTOR_NOT_FOUND, errMsg, e);
    }
    // Creates a new device instance.
    try {
      return deviceConstructor.newInstance(deviceControlId);
    } catch (ReflectiveOperationException e) {
      throw new MobileHarnessException(BasicErrorId.REFLECTION_CREATE_DEVICE_ERROR, errMsg, e);
    }
  }
}
