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

package com.google.devtools.mobileharness.platform.android.shared.constant;

import com.google.common.collect.ImmutableSet;

/**
 * Holds package related constants that are shared in Mobile Harness Lightning API
 * (go/mh-lightning-api).
 *
 * <p>Please keep all constants in this class sorted in alphabetical order by name.
 */
public final class PackageConstants {

  /** Package name of GMS core. */
  public static final String PACKAGE_NAME_GMS = "com.google.android.gms";

  /**
   * Package name for the androidx basic services apk at
   * //java/com/google/android/apps/common/testing/services:basic_services.apk
   */
  public static final String PACKAGE_NAME_BASIC_SERVICES_APK =
      "com.google.android.apps.common.testing.services";

  /**
   * Package name for the androidx test services apk at
   * //third_party/android/androidx_test/services:test_services.apk
   */
  public static final String PACKAGE_NAME_TEST_SERVICES_APK = "androidx.test.services";

  /**
   * List of package names for androidx services apks at
   * //java/com/google/android/apps/common/testing/services:basic_services.apk and
   * //third_party/android/androidx_test/services:test_services.apk
   */
  public static final ImmutableSet<String> ANDROIDX_SERVICES_APK_PACKAGE_NAMES =
      ImmutableSet.of(PACKAGE_NAME_BASIC_SERVICES_APK, PACKAGE_NAME_TEST_SERVICES_APK);

  private PackageConstants() {}
}
