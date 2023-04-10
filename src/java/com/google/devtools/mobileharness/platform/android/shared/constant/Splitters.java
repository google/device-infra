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

import com.google.common.base.Splitter;

/**
 * Holds {@link Splitter} constants that are shared in Mobile Harness Android platform.
 *
 * <p>Please keep all constants in this class sorted in alphabetical order by name.
 */
public final class Splitters {

  /** New line splitter. */
  public static final Splitter LINE_SPLITTER = Splitter.onPattern("\r\n|\n|\r");

  public static final Splitter LINE_OR_WHITESPACE_SPLITTER = Splitter.onPattern("\r\n|\n|\r|\\s+");

  private Splitters() {}
}
