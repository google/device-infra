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

package com.google.devtools.mobileharness.platform.android.sdktool.adb;

import com.google.common.collect.ImmutableList;

/** Android device properties. */
public enum AndroidProperty {
  ABI("ro.product.cpu.abi", "ro.product.cpu.abi2"),
  BOOT_TO_VR("ro.boot.vr"),
  BRAND("ro.product.brand"),
  BUILD("ro.build.display.id"),
  BUILD_TYPE("ro.build.type"),
  CHARACTERISTICS("ro.build.characteristics"),
  CODENAME("ro.build.version.codename"),
  DEVICE("ro.product.device"),
  FLAVOR("ro.build.flavor"),
  HARDWARE("ro.hardware"),
  HARDWARE_TYPE("ro.hardware.type"),
  INCREMENTAL_BUILD("ro.build.version.incremental"),
  KAIOS_RUNTIME_TOKEN("kaios.services.runtime.token"),
  LANGUAGE("persist.sys.language", "ro.product.locale.language"),
  LOCALE("persist.sys.locale"),
  MODEL("ro.product.model"),
  NATIVE_BRIDGE("ro.dalvik.vm.native.bridge"),
  PREVIEW_SDK_VERSION("ro.build.version.preview_sdk"),
  PRODUCT("ro.build.product"),
  PRODUCT_BOARD("ro.product.board", "ro.hardware"),
  REGION("persist.sys.country", "ro.product.locale.region"),
  RELEASE_VERSION("ro.build.version.release"),
  REVISION("ro.revision", "ro.boot.revision", "ro.boot.hardware.revision"),
  SCREEN_DENSITY("qemu.sf.lcd_density", "ro.sf.lcd_density"),
  SERIAL("ro.serialno"),
  SDK_VERSION("ro.build.version.sdk"),
  SIGN("ro.build.tags"),
  SOC_ID("ro.boot.hw.soc.id"),
  SOC_REV("ro.boot.hw.soc.rev"),
  TYPE("ro.product.name"),
  VERITYMODE("ro.boot.veritymode");

  /** Keys of the Android system property. */
  private final ImmutableList<String> keys;

  /**
   * Creates an enum type with the given Android system property keys. If we can get system property
   * with the first key, the remaining keys are not used. Else will use the second key and so on.
   */
  private AndroidProperty(String... keys) {
    this.keys = ImmutableList.<String>copyOf(keys);
  }

  /** Returns list of Android system property keys. */
  public ImmutableList<String> getPropertyKeys() {
    return keys;
  }
}
