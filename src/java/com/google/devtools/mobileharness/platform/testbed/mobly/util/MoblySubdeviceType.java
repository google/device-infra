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

package com.google.devtools.mobileharness.platform.testbed.mobly.util;

/**
 * Device types in a Mobly testbed that are also detectable by MobileHarness detectors directly.
 *
 * <p>When adding a new device to this enum, make sure to add the corresponding MH device library to
 * the runtime_deps of the MoblyConfigGenerator library, because it uses reflection to get the MH
 * device class.
 */
public enum MoblySubdeviceType {
  ANDROID_DEVICE(
      /* jsonTypeName= */ "AndroidDevice",
      /* jsonIdKey= */ "serial",
      /* mhClassName= */ "AndroidDevice"),
  // Mobly users can declare arbitrary controllers for things
  // MobileHarness has no notion of or support for (e.g. a Cellular call box, RF attenuators, giant
  // mech suit). Specifically, this generic testbed config:
  // "devices" : [{
  //         "id" : "A",  # This needs to be a unique id across all MiscTestbedSubDevices,
  //                      # but otherwise is not important to Mobly
  //         "type" : "MiscTestbedSubDevice",
  //         "dimensions" : {
  //             "mobly_type" : "Gundam",
  //         }
  //         "properties" : {
  //             "gundamModel": "RX-78-2"
  //         }
  //     }, {
  //         "id" : "B",
  //         "type" : "MiscTestbedSubDevice",
  //         "dimensions" : {
  //             "mobly_type" : "Gundam",
  //         }
  //         "properties" : {
  //             "gundamModel": "XXXG-00W0"
  //         }
  //     }, {
  //         "id" : "C",
  //         "type" : "MiscTestbedSubDevice",
  //         "dimensions" : {
  //             "mobly_type" : "TranslationDroid",
  //         }
  //         "properties" : {
  //             "name": "C-3PO"
  //         }
  //     }
  //
  // produces the Mobly Controllers section
  //
  // Controllers:
  //   Gundam:
  //   - gundamModel: RX-78-2
  //   - gundamModel: XXXG-00W0
  //   TranslationDroid:
  //   - name: C-3PO
  MISC_TESTBED_SUB_DEVICE(
      /* jsonTypeName= */ "mobly_type",
      "type" /* jsonIdKey (not used) */,
      /* mhClassName= */ "MiscTestbedSubDevice"),
  // Used for testing
  NO_OP_DEVICE(
      /* jsonTypeName= */ "MiscDevice", /* jsonIdKey= */ "id", /* mhClassName= */ "NoOpDevice");

  private final String jsonTypeName;
  private final String jsonIdKey;
  private final String mhClassName;

  private MoblySubdeviceType(String jsonTypeName, String jsonIdKey, String mhClassName) {
    this.jsonTypeName = jsonTypeName;
    this.jsonIdKey = jsonIdKey;
    this.mhClassName = mhClassName;
  }

  /** MobileHarness device class simple name, e.g., {@code AndroidRealDevice} for Android. */
  public String getMhClassName() {
    return mhClassName;
  }

  /** JSON key for device type, e.g., "AndroidDevice" for Android. */
  public String getJsonTypeName() {
    return jsonTypeName;
  }

  /**
   * JSON key inside the device tuple that contains the ID of the device, e.g., "serial" for
   * Android.
   */
  public String getJsonIdKey() {
    return jsonIdKey;
  }
}
