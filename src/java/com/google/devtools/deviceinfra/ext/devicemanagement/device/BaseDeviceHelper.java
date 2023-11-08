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

package com.google.devtools.deviceinfra.ext.devicemanagement.device;

import com.google.common.base.Ascii;
import com.google.common.collect.Multimap;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Duration;
import javax.annotation.Nullable;

/** Helper class for {@code BaseDevice}. */
public class BaseDeviceHelper {

  /**
   * Setup for a base device.
   *
   * @param device the device instance
   * @param deviceClazz the types in the inheritance chain from the actual class of {@code
   *     device}(included) to the {@code deviceClazz}(excluded) will be added to the supported
   *     device types
   * @param extraDimensions extra dimensions added to the {@code device}
   */
  public static void setUp(
      Device device,
      Class<? extends Device> deviceClazz,
      @Nullable Multimap<Dimension.Name, String> extraDimensions) {
    Class<?> clazz = device.getClass();

    while (clazz != deviceClazz) {
      String className = clazz.getSimpleName();
      // PartialMock in unit test may add some extra classes like
      // AndroidDeviceTest$AndroidDeviceImpl$$EnhancerByCGLIB$$87fc13dd, ignores them.
      if (!className.contains("$")) {
        device.info().deviceTypes().add(className);
      }
      clazz = clazz.getSuperclass();
    }
    device.updateDimension(
        Ascii.toLowerCase(Dimension.Name.SUPPORTS_ADHOC.name()), Dimension.Value.TRUE);

    if (extraDimensions != null) {
      extraDimensions
          .asMap()
          .entrySet()
          .forEach(e -> device.updateDimension(e.getKey(), e.getValue().toArray(new String[0])));
    }
  }

  /** Gets the default device setup timeout for a base device. */
  public static Duration getBaseDeviceSetupTimeout() {
    return Duration.ofMinutes(5L);
  }

  /** Gets the directory for saving device screenshots. */
  public static String getGenScreenshotPathWithDate(Device device) throws MobileHarnessException {
    String screenshotFileName =
        new SimpleDateFormat("yyyy_MM_dd_hh_mm_ss_SSS")
                .format(new Timestamp(System.currentTimeMillis()))
            + ".png";
    return PathUtil.join(device.getGenFileDir(), screenshotFileName);
  }

  private BaseDeviceHelper() {}
}
