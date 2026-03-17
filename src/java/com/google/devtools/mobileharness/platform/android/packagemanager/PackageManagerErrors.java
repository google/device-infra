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

package com.google.devtools.mobileharness.platform.android.packagemanager;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;

/** Error codes and handling for Android's package manager. */
public final class PackageManagerErrors {

  private record ErrorDetail(String packageManagerError, AndroidErrorId errorId, String helpText) {}

  private static final ImmutableList<ErrorDetail> BAD_USER_CONFIG_INSTALL_FAILURES =
      ImmutableList.of(
          new ErrorDetail(
              "INSTALL_FAILED_CPU_ABI_INCOMPATIBLE",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_CPU_ABI_INCOMPATIBLE,
              "Package contains native code, but non is compatible with the device's ABI"),
          new ErrorDetail(
              "INSTALL_FAILED_DEPRECATED_SDK_VERSION",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_DEPRECATED_SDK_VERSION,
              "Package targets a deprecated SDK version"),
          new ErrorDetail(
              "INSTALL_FAILED_DUPLICATE_PERMISSION",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_DUPLICATE_PERMISSION,
              "Package failed because it wants to define a permission already defined"),
          new ErrorDetail(
              "INSTALL_FAILED_INVALID_APK: Split null was defined multiple times",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_INVALID_APK_SPLIT_NULL,
              "Installation failed due to duplication of splits or duplication of apps"),
          new ErrorDetail(
              "INSTALL_FAILED_INVALID_APK",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_INVALID_APK,
              "Package is not a valid APK"),
          new ErrorDetail(
              "INSTALL_FAILED_MISSING_FEATURE",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_MISSING_FEATURE,
              "Package uses a feature not supported by the device"),
          new ErrorDetail(
              "INSTALL_FAILED_MISSING_SHARED_LIBRARY",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_MISSING_SHARED_LIBRARY,
              "Package is missing a shared library. Check the <uses-library> element in the"
                  + " manifest."),
          new ErrorDetail(
              "INSTALL_FAILED_MISSING_SPLIT",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_MISSING_SPLIT,
              "Package is missing one or more splits"),
          new ErrorDetail(
              "INSTALL_FAILED_NEWER_SDK",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_NEWER_SDK,
              "Package requires an older device SDK version"),
          new ErrorDetail(
              "INSTALL_FAILED_NO_MATCHING_ABIS",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_ABI_INCOMPATIBLE,
              "Package has native code, but none is compatible with the device's ABI"),
          new ErrorDetail(
              "INSTALL_FAILED_OLDER_SDK",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_OLDER_SDK,
              "Package requires a newer device SDK version"),
          new ErrorDetail(
              "INSTALL_FAILED_SHARED_USER_INCOMPATIBLE",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_SHARED_USER_INCOMPATIBLE,
              "Package requested shared user which is already installed with non-matching"
                  + " signatures"),
          new ErrorDetail(
              "INSTALL_FAILED_TEST_ONLY",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_TEST_ONLY,
              "Package is marked as test only, but installation was not requested to allow test"
                  + " only packages"),
          new ErrorDetail(
              "INSTALL_FAILED_VERIFICATION_FAILURE",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_FAILED_VERIFICATION_FAILURE,
              "Package verification failed"),
          new ErrorDetail(
              "INSTALL_PARSE_FAILED_BAD_MANIFEST",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_PARSE_FAILED_BAD_MANIFEST,
              "Parser encountered an error in manifest"),
          new ErrorDetail(
              "INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_PARSE_FAILED_BAD_PACKAGE_NAME,
              "Parser encountered an invalid package name"),
          new ErrorDetail(
              "INSTALL_PARSE_FAILED_BAD_SHARED_USER_ID",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_PARSE_FAILED_BAD_SHARED_USER_ID,
              "Parser encountered an invalid shared user ID"),
          new ErrorDetail(
              "INSTALL_PARSE_FAILED_CERTIFICATE_ENCODING",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_PARSE_FAILED_CERTIFICATE_ENCODING,
              "Parser encountered an invalid certificate encoding"),
          new ErrorDetail(
              "INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES",
              AndroidErrorId
                  .ANDROID_PKG_MNGR_UTIL_INSTALLATION_PARSE_FAILED_INCONSISTENT_CERTIFICATES,
              "Ecountered certificate signature mismatch for a shared user"),
          new ErrorDetail(
              "INSTALL_PARSE_FAILED_MANIFEST_EMPTY",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_PARSE_FAILED_MANIFEST_EMPTY,
              "Parser encountered an empty manifest"),
          new ErrorDetail(
              "INSTALL_PARSE_FAILED_MANIFEST_MALFORMED",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_PARSE_FAILED_MANIFEST_MALFORMED,
              "Parser encountered a malformed manifest"),
          new ErrorDetail(
              "INSTALL_PARSE_FAILED_NO_CERTIFICATES",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_PARSE_FAILED_NO_CERTIFICATES,
              "No certificates found in package"),
          new ErrorDetail(
              "INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_PARSE_FAILED_UNEXPECTED_EXCEPTION,
              "Unexpected error when parsing the package"));

  private static final ImmutableList<ErrorDetail> BAD_NO_RETRY_USER_CONFIG_INSTALL_FAILURES =
      ImmutableList.of(
          new ErrorDetail(
              "INSTALL_FAILED_DEXOPT",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_DEXOPT,
              "Package install failed optimizing and validating"),
          new ErrorDetail(
              "INSTALL_FAILED_PERMISSION_MODEL_DOWNGRADE",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_PERMISSION_MODEL_DOWNGRADE,
              "Package does not support runtime permissions but the installed package does"),
          // Can occur for pre-installed system apps where signatures don't match, even after
          // uninstall.
          new ErrorDetail(
              "INSTALL_FAILED_UPDATE_INCOMPATIBLE",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_UPDATE_INCOMPATIBLE,
              "Package is already installed and is not compatible with the update"),
          new ErrorDetail(
              "INSTALL_FAILED_VERSION_DOWNGRADE",
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_VERSION_DOWNGRADE,
              "Package version is lower than the installed version"));

  /**
   * Throws an exception for well-known installation errors detected in the Package Manager error
   * output, otherwise does nothing.
   *
   * <p>These are errors that are not recoverable by retrying the installation.
   */
  public static void throwIfUnrecoverableUserError(String errorText, String packageName)
      throws MobileHarnessException {
    for (ErrorDetail errorDetail : BAD_USER_CONFIG_INSTALL_FAILURES) {
      if (errorText.contains(errorDetail.packageManagerError())) {
        throw new MobileHarnessException(
            errorDetail.errorId(),
            String.format(
                "Failed to install %s due to error %s: %s",
                packageName, errorDetail.packageManagerError(), errorDetail.helpText()));
      }
    }
  }

  /**
   * Throws an exception for well-known installation errors detected in the Package Manager error
   * output, otherwise does nothing.
   *
   * <p>For additional cases of {@link #throwIfUnrecoverableUserError} after installation retries,
   * or cases where no retry is attempted.
   */
  public static void throwIfPostRetryUserError(String errorText, String packageName)
      throws MobileHarnessException {
    for (ErrorDetail errorDetail : BAD_NO_RETRY_USER_CONFIG_INSTALL_FAILURES) {
      if (errorText.contains(errorDetail.packageManagerError())) {
        throw new MobileHarnessException(
            errorDetail.errorId(),
            String.format(
                "Failed to install %s due to error %s: %s",
                packageName, errorDetail.packageManagerError(), errorDetail.helpText()));
      }
    }
  }

  private PackageManagerErrors() {}
}
