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

package com.google.wireless.qa.mobileharness.shared.util;

import com.google.auto.value.AutoValue;

/** The screen resolution of a device. */
@AutoValue
public abstract class ScreenResolution {

  /** The creator with only physical width and height. */
  public static ScreenResolution create(int width, int height) {
    return createWithOverride(width, height, width, height);
  }

  /** The creator with override width and height. */
  public static ScreenResolution createWithOverride(
      int width, int height, int curWidth, int curHeight) {
    return new AutoValue_ScreenResolution(width, height, curWidth, curHeight);
  }

  /** The physical width of the screen. */
  public abstract int width();

  /** The physical height of the screen. */
  public abstract int height();

  /** The override with of the screen. */
  public abstract int curWidth();

  /** The override height of the screen. */
  public abstract int curHeight();
}
