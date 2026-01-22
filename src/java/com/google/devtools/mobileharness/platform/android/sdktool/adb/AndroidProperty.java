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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Android device properties.
 *
 * <p>Each enum item (except those annotated with {@link DoNotAddToDimension}) will be added to
 * device dimensions, whose key is the lower case of the enum name, and whose value is the lower
 * case of device property value (if the enum item is annotated with {@link KeepDimensionValueCase},
 * the dimension value will be a list containing the original device property value and the lower
 * case string). If the device property value is empty, the dimension will not be added.
 *
 * @see
 *     com.google.devtools.mobileharness.platform.android.device.AndroidDeviceHelper#updateAndroidPropertyDimensions
 */
public enum AndroidProperty {
  // keep-sorted start sticky_prefixes=@
  ABI("ro.product.cpu.abi", "ro.product.cpu.abi2"),
  ABILIST("ro.product.cpu.abilist"),
  BASEBAND_VERSION("gsm.version.baseband"),
  BOOT_TO_VR("ro.boot.vr"),
  BRAND("ro.product.brand"),
  @KeepDimensionValueCase
  BUILD("ro.build.display.id"),
  BUILD_ALIAS("ro.build.id"),
  BUILD_TYPE("ro.build.type"),
  CHARACTERISTICS("ro.build.characteristics"),
  CODENAME("ro.build.version.codename"),
  COLOR("ro.boot.hardware.color"),
  DEVICE("ro.product.vendor.device", "ro.vendor.product.device", "ro.product.device"),
  @DoNotAddToDimension
  DISABLE_CALL("ro.telephony.disable-call"),
  @DoNotAddToDimension
  FLAVOR("ro.build.flavor"),
  GSM_OPERATOR_ALPHA("gsm.operator.alpha"),
  HARDWARE("ro.hardware"),
  HARDWARE_TYPE("ro.hardware.type"),
  HARDWARE_UFS("ro.boot.hardware.ufs"),
  HAS_RAMDUMP("vendor.debug.ramdump.status.has_ramdump"),
  INCREMENTAL_BUILD("ro.build.version.incremental"),
  @DoNotAddToDimension
  KAIOS_RUNTIME_TOKEN("kaios.services.runtime.token"),
  LANGUAGE("persist.sys.language", "ro.product.locale.language"),
  LOCALE("persist.sys.locale"),
  MANUFACTURER("ro.product.manufacturer"),
  MEMORY_CLASS("dalvik.vm.heapgrowthlimit", "dalvik.vm.heapsize"),
  MODEL("ro.product.model"),
  NATIVE_BRIDGE("ro.dalvik.vm.native.bridge"),
  NONSEC_AR("ro.boot.hw.soc.nonsec-ar"),
  @DoNotAddToDimension
  PERSIST_TEST_HARNESS("persist.sys.test_harness"),
  PREVIEW_SDK_VERSION("ro.build.version.preview_sdk"),
  @DoNotAddToDimension
  PRODUCT("ro.build.product"),
  PRODUCT_BOARD("ro.product.board", "ro.hardware"),
  PRODUCT_DEVICE("ro.product.device"),
  REGION("persist.sys.country", "ro.product.locale.region"),
  RELEASE_VERSION("ro.build.version.release"),
  REVISION("ro.revision", "ro.boot.revision", "ro.boot.hardware.revision"),
  SCREEN_DENSITY("qemu.sf.lcd_density", "ro.sf.lcd_density"),
  SDK_FULL_VERSION("ro.build.version.sdk_full"),
  SDK_VERSION("ro.build.version.sdk"),
  SECURE_BOOT("ro.boot.secure_boot"),
  SEC_AR("ro.boot.hw.soc.sec-ar"),
  @KeepDimensionValueCase
  SERIAL("ro.serialno"),
  SIGN("ro.build.tags"),
  @DoNotAddToDimension
  SILENT("ro.audio.silent"),
  SIM_OPERATOR_ALPHA("gsm.sim.operator.alpha"),
  SIM_STATE("gsm.sim.state"),
  SOC_ID("ro.boot.hw.soc.id"),
  SOC_REV("ro.boot.hw.soc.rev"),
  @DoNotAddToDimension
  TEST_HARNESS("ro.test_harness"),
  TYPE("ro.product.name"),
  VERITYMODE("ro.boot.veritymode");

  // keep-sorted end

  /** Keys of the Android system property. */
  private final ImmutableList<String> keys;

  /**
   * Creates an enum type with the given Android system property keys. If we can get system property
   * with the first key, the remaining keys are not used. Else will use the second key and so on.
   */
  AndroidProperty(String... keys) {
    checkArgument(keys.length > 0);
    this.keys = ImmutableList.copyOf(keys);
  }

  /** Returns the list of Android system property keys whose length >= 1. */
  public ImmutableList<String> getPropertyKeys() {
    return keys;
  }

  /** Gets the first element in {@link #getPropertyKeys()}. */
  public String getPrimaryPropertyKey() {
    return keys.get(0);
  }

  /** Indicate that the enum item will not be added to device dimensions. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.FIELD)
  public @interface DoNotAddToDimension {}

  /**
   * Indicate that when the enum item is added as a device dimension, the dimension value list will
   * be the original device property value string and its lower case string, instead of a list
   * containing only the lower case of the device property value string.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.FIELD)
  public @interface KeepDimensionValueCase {}
}
