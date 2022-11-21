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

package com.google.devtools.mobileharness.api.model.error;

import com.google.common.base.Preconditions;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.common.metrics.stability.util.ErrorIdFormatter;

/**
 * Error IDs for Mobile Harness Android platform supports, or Android related Driver/Decorator,
 * Detector/Device implementations. *
 */
public enum AndroidErrorId implements ErrorId {
  // ***********************************************************************************************
  // Standard Android Platforms: 100_001 ~ 170_000
  // ***********************************************************************************************

  // AndroidPackageManagerUtil: 101_301 ~ 101_600
  ANDROID_PKG_MNGR_UTIL_CLEAR_PACKAGE_ERROR(101_301, ErrorType.DEPENDENCY_ISSUE),
  ANDROID_PKG_MNGR_UTIL_DISABLE_PACKAGE_ERROR(101_302, ErrorType.DEPENDENCY_ISSUE),
  ANDROID_PKG_MNGR_UTIL_DUMPSYS_ERROR(101_303, ErrorType.DEPENDENCY_ISSUE),
  ANDROID_PKG_MNGR_UTIL_INVALID_VERSION(101_304, ErrorType.DEPENDENCY_ISSUE),
  ANDROID_PKG_MNGR_UTIL_GET_VERSION_INFO_ERROR(101_305, ErrorType.DEPENDENCY_ISSUE),
  ANDROID_PKG_MNGR_UTIL_GET_INSTALLED_APK_PATH_ERROR(101_306, ErrorType.INFRA_ISSUE),
  ANDROID_PKG_MNGR_UTIL_PM_PATH_NO_PACKAGE_FOUND(101_307, ErrorType.INFRA_ISSUE),
  ANDROID_PKG_MNGR_UTIL_GRANT_PERMISSION_ERROR(101_308, ErrorType.INFRA_ISSUE),
  ANDROID_PKG_MNGR_UTIL_GET_PACKAGE_NAME_ERROR(101_309, ErrorType.DEPENDENCY_ISSUE),
  ANDROID_PKG_MNGR_UTIL_INSTALLATION_APP_BLACKLISTED(101_310, ErrorType.CUSTOMER_ISSUE),
  /**
   * @deprecated Please use ANDROID_PKG_MNGR_UTIL_INSTALLATION_ERROR_IN_SHARED_LAB or
   *     ANDROID_PKG_MNGR_UTIL_INSTALLATION_ERROR_IN_SATELLITE_LAB
   */
  @Deprecated
  ANDROID_PKG_MNGR_UTIL_INSTALLATION_ERROR(101_311, ErrorType.UNDETERMINED),
  ANDROID_PKG_MNGR_UTIL_INSTALLATION_ABI_INCOMPATIBLE(101_312, ErrorType.CUSTOMER_ISSUE),
  ANDROID_PKG_MNGR_UTIL_INSTALLATION_MISSING_SHARED_LIBRARY(101_313, ErrorType.DEPENDENCY_ISSUE),
  ANDROID_PKG_MNGR_UTIL_INSTALLATION_UPDATE_INCOMPATIBLE(101_314, ErrorType.CUSTOMER_ISSUE),
  ANDROID_PKG_MNGR_UTIL_LIST_PACKAGES_ERROR(101_315, ErrorType.DEPENDENCY_ISSUE),
  ANDROID_PKG_MNGR_UTIL_GET_PACKAGE_LIST_ERROR(101_316, ErrorType.DEPENDENCY_ISSUE),
  ANDROID_PKG_MNGR_UTIL_UNINSTALLATION_ERROR(101_317, ErrorType.UNDETERMINED),
  ANDROID_PKG_MNGR_UTIL_GET_APK_ABI_ERROR(101_318, ErrorType.CUSTOMER_ISSUE),
  ANDROID_PKG_MNGR_UTIL_GET_APK_MIN_SDK_VERSION_ERROR(101_319, ErrorType.CUSTOMER_ISSUE),
  ANDROID_PKG_MNGR_UTIL_GET_APK_PACKAGE_NAME_ERROR(101_320, ErrorType.CUSTOMER_ISSUE),
  ANDROID_PKG_MNGR_UTIL_GET_APK_VERSION_CODE_ERROR(101_321, ErrorType.CUSTOMER_ISSUE),
  ANDROID_PKG_MNGR_UTIL_GET_APK_VERSION_NAME_ERROR(101_322, ErrorType.CUSTOMER_ISSUE),
  ANDROID_PKG_MNGR_UTIL_SDK_VERSION_NOT_SUPPORT(101_323, ErrorType.DEPENDENCY_ISSUE),
  ANDROID_PKG_MNGR_UTIL_GET_DEVICE_PROP_ERROR(101_324, ErrorType.INFRA_ISSUE),
  ANDROID_PKG_MNGR_UTIL_GET_APEX_MODULE_VERSION_CODE_ERROR(101_325, ErrorType.INFRA_ISSUE),
  ANDROID_PKG_MNGR_UTIL_INVALID_APEX_VERSION_CODE(101_326, ErrorType.DEPENDENCY_ISSUE),
  ANDROID_PKG_MNGR_UTIL_MISSING_APEX_VERSION_CODE(101_327, ErrorType.DEPENDENCY_ISSUE),
  ANDROID_PKG_MNGR_UTIL_PARTIAL_INSTALL_NOT_ALLOWED_ERROR(101_328, ErrorType.CUSTOMER_ISSUE),
  ANDROID_PKG_MNGR_UTIL_INSTALLATION_VERSION_DOWNGRADE(101_329, ErrorType.DEPENDENCY_ISSUE),
  ANDROID_PKG_MNGR_UTIL_INSTALLATION_INSUFFICIENT_STORAGE(101_330, ErrorType.DEPENDENCY_ISSUE),
  ANDROID_PKG_MNGR_UTIL_INSTALLATION_ERROR_IN_SHARED_LAB(101_331, ErrorType.INFRA_ISSUE),
  ANDROID_PKG_MNGR_UTIL_INSTALLATION_ERROR_IN_SATELLITE_LAB(101_332, ErrorType.CUSTOMER_ISSUE),
  ANDROID_PKG_MNGR_UTIL_INSTALLATION_PARSE_FAILED_MANIFEST_MALFORMED(
      101_333, ErrorType.CUSTOMER_ISSUE),
  ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_INVALID_APK(101_334, ErrorType.CUSTOMER_ISSUE),
  ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_DUPLICATE_PERMISSION(101_335, ErrorType.CUSTOMER_ISSUE),
  ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_OLDER_SDK(101_336, ErrorType.CUSTOMER_ISSUE),
  ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_NO_VALID_UID_ASSIGNED(
      101_337, ErrorType.DEPENDENCY_ISSUE),

  ANDROID_ERROR_ID_PLACE_HOLDER_TO_BE_RENAMED(200_000, ErrorType.UNDETERMINED);

  public static final int MIN_CODE = ExtErrorId.MAX_CODE + 1;
  public static final int MAX_CODE = 200_000;

  private final int code;
  private final ErrorType type;

  AndroidErrorId(int code, ErrorType type) {
    Preconditions.checkArgument(code >= MIN_CODE);
    Preconditions.checkArgument(code <= MAX_CODE);
    Preconditions.checkArgument(type != ErrorType.UNCLASSIFIED);
    this.code = code;
    this.type = type;
  }

  @Override
  public int code() {
    return code;
  }

  @Override
  public ErrorType type() {
    return type;
  }

  @Override
  public String toString() {
    return ErrorIdFormatter.formatErrorId(this);
  }
}
