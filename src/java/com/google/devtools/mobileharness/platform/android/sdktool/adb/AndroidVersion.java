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

import static com.google.common.collect.ImmutableRangeMap.toImmutableRangeMap;
import static java.util.Arrays.stream;
import static java.util.function.Function.identity;

import com.google.common.collect.ImmutableRangeMap;
import com.google.common.collect.Range;
import java.util.Optional;

/**
 * Android versions with their basic information.
 *
 * <p>Follow the codename in <a
 * href="https://source.android.com/setup/start/build-numbers">build-numbers</a>.
 */
public enum AndroidVersion {
  CUPCAKE("C", 3, 3),
  DONUT("D", 4, 4),
  ECLAIR("E", 5, 7),
  FROYO("F", 8, 8),
  GINGER_BREAD("G", 9, 10),
  HONEYCOMB("H", 11, 13),
  ICE_CREAM_SANDWICH("I", 14, 15),
  JELLY_BEAN("J", 16, 18),
  KITKAT("K", 19, 20),
  LOLLIPOP("L", 21, 22),
  MARSHMALLOW("M", 23, 23),
  NOUGAT("N", 24, 25),
  OREO("O", 26, 27),
  PI("P", 28, 28),
  ANDROID_10("Q", 29, 29),
  ANDROID_11("R", 30, 30),
  ANDROID_12("S", 31, 31),
  ANDROID_12L("S", 32, 32),
  ANDROID_13("T", 33, 33),
  ANDROID_14("U", 34, 34);

  private final String codeNameInitial;
  private final Range<Integer> sdkRange;

  private static final AndroidVersion LATEST_ANDROID_VERSION;

  private static final ImmutableRangeMap<Integer, AndroidVersion> SDK_MAP;

  static {
    SDK_MAP =
        stream(AndroidVersion.values()).collect(toImmutableRangeMap(av -> av.sdkRange, identity()));
    LATEST_ANDROID_VERSION = SDK_MAP.get(SDK_MAP.span().upperEndpoint());
  }

  AndroidVersion(String codeNameInitial, int startSdkVersion, int endSdkVersion) {
    this.codeNameInitial = codeNameInitial;
    this.sdkRange = Range.closed(startSdkVersion, endSdkVersion);
  }

  /** Returns start SDK version for given {@link AndroidVersion}. */
  public int getStartSdkVersion() {
    return sdkRange.lowerEndpoint();
  }

  /** Returns end SDK version for given {@link AndroidVersion}. */
  public int getEndSdkVersion() {
    return sdkRange.upperEndpoint();
  }

  /** Returns the Android version for the sdk. */
  public static Optional<AndroidVersion> of(int sdk) {
    return Optional.ofNullable(SDK_MAP.get(sdk));
  }

  /** Returns the code name initial for the sdk. */
  public static Optional<String> toCodeNameInitial(int sdk) {
    return of(sdk).map(av -> av.codeNameInitial);
  }

  public static AndroidVersion getLatestAndroidVersion() {
    return LATEST_ANDROID_VERSION;
  }
}
