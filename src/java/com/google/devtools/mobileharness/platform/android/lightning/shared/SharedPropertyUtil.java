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

package com.google.devtools.mobileharness.platform.android.lightning.shared;

import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.util.Map.Entry;

/** Static device properties related utilities shared by Mobile Harness Lightning APIs. */
public final class SharedPropertyUtil {

  /** Clears the device properties with the given prefix. */
  public static void clearPropertiesWithPrefix(Device device, String propertyNamePrefix) {
    for (Entry<String, String> property : device.getProperties().entrySet()) {
      String propertyName = property.getKey();
      if (!propertyName.startsWith(propertyNamePrefix)) {
        continue;
      }
      if (property.getValue() != null) {
        device.setProperty(propertyName, null);
      }
    }
  }

  private SharedPropertyUtil() {}
}
