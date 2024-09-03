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

package com.google.devtools.mobileharness.infra.ats.common;

import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;

/** xTS property name constants. */
public interface XtsPropertyName extends PropertyName {

  /** xTS job property name constants. */
  enum Job implements XtsPropertyName {
    IS_XTS_TF_JOB,

    IS_XTS_NON_TF_JOB,

    IS_RUN_RETRY,

    PREV_SESSION_DEVICE_BUILD_FINGERPRINT,

    PREV_SESSION_DEVICE_BUILD_FINGERPRINT_UNALTERED,

    PREV_SESSION_DEVICE_VENDOR_BUILD_FINGERPRINT,

    PREV_SESSION_HAS_TF_MODULE,

    PREV_SESSION_HAS_NON_TF_MODULE,

    SKIP_COLLECTING_NON_TF_REPORTS,

    SKIP_COLLECTING_DEVICE_INFO,
  }
}
