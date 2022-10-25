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

/**
 * Types of dumpsys.
 *
 * @see <a href="http://wrightrocket.blogspot.com/2010/12/useful-commands-in-adb-shell.html">Using
 *     dumpsys commands in the Android adb shell</a>
 */
public enum DumpSysType {
  ACCOUNT("account"), // Account information
  ACTIVITY("activity"), // Activity information
  BATTERY("battery"), // Battery information
  BATTERYSTATS("batterystats"), // Batterystats information
  CAMERA("media.camera"), // Camera information
  CONNECTIVITY("connectivity"), // Network connectivity
  CPUINFO("cpuinfo"), // Processor usage
  DISPLAY("display"), // Information of the keyboards, windows and their z order
  GFXINFO("gfxinfo"), // GPU rendering information
  MEMINFO("meminfo"), // Memory usage
  NONE(""), // Use it to for all types as command "adb shell dumpsys".
  PACKAGE("package"), // Package info of applications
  PROCSTATS("procstats"), // statistics of app's runtime, PSS and USS
  POWER("power"), // Power manager information
  WIFI("wifi"), // Available access points and current connection
  WIFISCANNER("wifiscanner"), // Scanned wifi information
  WINDOW("window"), // Display information
  INPUT("input"); // The state of the system’s input devices, such as keyboards and touchscreens,
  // and processing of input events. See https://source.android.com/devices/input/diagnostics.html

  private final String dumpSysTypeValue;

  private DumpSysType(String dumpSysTypeValue) {
    this.dumpSysTypeValue = dumpSysTypeValue;
  }

  /** Returns the String value of DumpSysType. */
  public String getTypeValue() {
    return dumpSysTypeValue;
  }
}
