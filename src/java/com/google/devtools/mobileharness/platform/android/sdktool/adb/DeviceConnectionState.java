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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Arrays.stream;
import static java.util.function.Function.identity;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;

/** Device state defined by adb. */
public enum DeviceConnectionState {
  DEVICE("device"),
  RECOVERY("recovery"),
  RESCUE("rescue"),
  SIDELOAD("sideload"),
  BOOTLOADER("bootloader"),
  DISCONNECT("disconnect");

  private final String name;
  private final String waitForArg;

  private static final ImmutableMap<String, DeviceConnectionState> NAME_MAP;

  static {
    NAME_MAP =
        stream(DeviceConnectionState.values()).collect(toImmutableMap(as -> as.name, identity()));
  }

  DeviceConnectionState(String name) {
    this.name = name;
    this.waitForArg = "wait-for-" + name;
  }

  /** Gets the name of the state. */
  public String getName() {
    return name;
  }

  /** Gets the arg for {@code adb wait-for-STATE} command */
  public String getWaitForArg() {
    return waitForArg;
  }

  /** The possible {@link DeviceConnectionState} of the name. */
  public static Optional<DeviceConnectionState> of(String name) {
    return Optional.ofNullable(NAME_MAP.get(name));
  }
}
