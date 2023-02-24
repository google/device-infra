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

package com.google.devtools.mobileharness.api.devicemanager.util;

import com.google.devtools.mobileharness.api.devicemanager.dispatcher.Dispatcher;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.util.ReflectionUtil;

/** Util for using class reflection to get class from class name. */
public final class ClassUtil {
  /**
   * Gets the sub-class of {@link Dispatcher} according to the given class simple name. The target
   * class should be in package {@link
   * com.google.devtools.mobileharness.api.devicemanager.dispatcher}.
   *
   * @param dispatcherClassSimpleName simple name of the dispatcher class
   * @throws MobileHarnessException if can not find the class
   */
  public static Class<? extends Dispatcher> getDispatcherClass(String dispatcherClassSimpleName)
      throws MobileHarnessException {
    return getDispatcherClass(dispatcherClassSimpleName, Dispatcher.class.getPackage().getName());
  }

  /**
   * Gets the sub-class of {@link Dispatcher} according to the given device type. The package of the
   * target class is derived from the package of the Device sub-class. E.g. if the Device sub-class
   * is in package {@link com.google.wireless.qa.mobileharness.shared.api.device}, then the target
   * class should be in package {@link
   * com.google.devtools.mobileharness.api.devicemanager.dispatcher}.
   *
   * @param deviceClass the device class type
   * @throws MobileHarnessException if can not find the class
   */
  public static Class<? extends Dispatcher> getDispatcherClass(Class<? extends Device> deviceClass)
      throws MobileHarnessException {
    return getDispatcherClass(
        deviceClass.getSimpleName() + "Dispatcher", Dispatcher.class.getPackage().getName());
  }

  /**
   * Gets the sub-class of {@link Dispatcher} according to the given class simple name and package
   * name.
   *
   * @param dispatcherClassSimpleName simple name of the dispatcher class
   * @param packageName name of the package containing the dispatcher class
   * @throws MobileHarnessException if can not find the class
   */
  public static Class<? extends Dispatcher> getDispatcherClass(
      String dispatcherClassSimpleName, String packageName) throws MobileHarnessException {
    try {
      return ReflectionUtil.getClass(dispatcherClassSimpleName, Dispatcher.class, packageName);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.CLASS_DISPATCHER_CLASS_NOT_FOUND,
          "Dispatcher \"" + dispatcherClassSimpleName + "\" does not exist",
          e);
    }
  }

  /**
   * Gets the sub-class of {@link Device} according to the given class simple name. The target class
   * should be in package {@link com.google.wireless.qa.mobileharness.shared.api.device}.
   *
   * @param deviceClassSimpleName simple name of the target device class
   * @throws MobileHarnessException if can not find the class
   */
  public static Class<? extends Device> getDeviceClass(String deviceClassSimpleName)
      throws MobileHarnessException {
    return getDeviceClass(deviceClassSimpleName, Device.class.getPackage().getName());
  }

  /**
   * Gets the sub-class of {@link Device} according to the given dispatcher type. The package of the
   * target class is derived from the package of the Detector sub-class. E.g. if the Dispatcher
   * sub-class is in package {@link com.google.devtools.mobileharness.api.devicemanager.dispatcher},
   * then the target class should be in package {@link
   * com.google.wireless.qa.mobileharness.shared.api.device}.
   *
   * @param dispatcherClass the device class type
   * @throws MobileHarnessException if can not find the class
   */
  public static Class<? extends Device> getDeviceClass(Class<? extends Dispatcher> dispatcherClass)
      throws MobileHarnessException {
    return getDeviceClass(
        getDeviceClassSimpleNameOfDispatcher(dispatcherClass), Device.class.getPackage().getName());
  }

  /**
   * Gets the sub-class of {@link Device} according to the given class simple name and package name.
   *
   * @param deviceClassSimpleName simple name of the target device class
   * @param packageName name of the package containing the target device class
   * @throws MobileHarnessException if can not find the class
   */
  public static Class<? extends Device> getDeviceClass(
      String deviceClassSimpleName, String packageName) throws MobileHarnessException {
    try {
      return ReflectionUtil.getClass(deviceClassSimpleName, Device.class, packageName);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.CLASS_DEVICE_CLASS_NOT_FOUND,
          "Device class [" + deviceClassSimpleName + "] does not exist",
          e);
    }
  }

  /**
   * Gets the class simple name of the {@link Device} class of the given dispatcher type.
   *
   * <p>See {@link #getDeviceClass(Class)}.
   */
  public static String getDeviceClassSimpleNameOfDispatcher(
      Class<? extends Dispatcher> dispatcherClass) {
    return dispatcherClass.getSimpleName().replaceAll("Dispatcher$", "");
  }

  private ClassUtil() {}
}
