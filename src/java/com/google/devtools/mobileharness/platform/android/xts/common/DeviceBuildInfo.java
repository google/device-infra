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

package com.google.devtools.mobileharness.platform.android.xts.common;

import com.google.devtools.mobileharness.infra.ats.console.result.xml.XmlConstants;

/**
 * Device build related info need to be shown in the <Build> element in the report. Mimic from
 * {@code com.android.compatibility.common.tradefed.targetprep.DeviceInfoCollector}, {@code
 * com.android.tradefed.invoker.InvocationExecution} for device_kernel_info, system_img_info and
 * vendor_img_info.
 */
public enum DeviceBuildInfo {
  ABI("build_abi", "ro.product.cpu.abi"),
  ABI2("build_abi2", "ro.product.cpu.abi2"),
  ABIS("build_abis", "ro.product.cpu.abilist"),
  ABIS_32("build_abis_32", "ro.product.cpu.abilist32"),
  ABIS_64("build_abis_64", "ro.product.cpu.abilist64"),
  BOARD("build_board", "ro.product.board"),
  BOOTIMAGE_FINGERPRINT("build_bootimage_fingerprint", "ro.bootimage.build.fingerprint"),
  BRAND("build_brand", "ro.product.brand"),
  DEVICE("build_device", "ro.product.device"),
  FINGERPRINT("build_fingerprint", "ro.build.fingerprint"),
  ID("build_id", "ro.build.id"),
  MANUFACTURER("build_manufacturer", "ro.product.manufacturer"),
  MODEL("build_model", "ro.product.model"),
  PRODUCT("build_product", "ro.product.name"),
  REFERENCE_FINGERPRINT("build_reference_fingerprint", "ro.build.reference.fingerprint"),
  SERIAL("build_serial", "ro.serialno"),
  TAGS("build_tags", "ro.build.tags"),
  TYPE("build_type", "ro.build.type"),
  VENDOR_FINGERPRINT("build_vendor_fingerprint", "ro.vendor.build.fingerprint"),
  VERSION_BASE_OS("build_version_base_os", "ro.build.version.base_os"),
  VERSION_INCREMENTAL("build_version_incremental", "ro.build.version.incremental"),
  VERSION_RELEASE("build_version_release", "ro.build.version.release"),
  VERSION_SDK("build_version_sdk", "ro.build.version.sdk"),
  VERSION_SECURITY_PATCH("build_version_security_patch", "ro.build.version.security_patch"),
  SYSTEM_IMG_INFO(XmlConstants.SYSTEM_IMG_INFO_ATTR, "ro.system.build.fingerprint"),
  VENDOR_IMG_INFO(XmlConstants.VENDOR_IMG_INFO_ATTR, "ro.vendor.build.fingerprint");

  private final String attributeName;
  private final String propName;

  private DeviceBuildInfo(String attributeName, String propName) {
    this.attributeName = attributeName;
    this.propName = propName;
  }

  public String getAttributeName() {
    return attributeName;
  }

  public String getPropName() {
    return propName;
  }
}
