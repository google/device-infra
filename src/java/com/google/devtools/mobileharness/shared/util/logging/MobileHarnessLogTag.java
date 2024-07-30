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

package com.google.devtools.mobileharness.shared.util.logging;

import com.google.devtools.mobileharness.shared.context.InvocationContext.InvocationType;

/** Utility to add logging tags. */
public final class MobileHarnessLogTag {

  public static final String DEVICE_ID = "device_id";

  public static final String SUB_DEVICE_ID = "sub_device_id";

  public static final String SESSION_ID = "session_id";

  public static final String JOB_ID = InvocationType.OMNILAB_JOB.displayName();

  public static final String TEST_ID = InvocationType.OMNILAB_TEST.displayName();

  public static void addTag(String tag, String value) {}

  private MobileHarnessLogTag() {}
}
