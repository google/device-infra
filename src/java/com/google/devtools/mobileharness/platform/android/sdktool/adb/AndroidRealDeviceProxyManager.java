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

import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.util.HashMap;
import java.util.Map;

/**
 * The RealDeviceProxyManager records the <device serial, local tcp address> pairs. It's used when
 * we start a tcp proxy over an USB device.
 */
public class AndroidRealDeviceProxyManager {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Lock used whenever accessing the realDeviceProxyMap. */
  private static final Object lock = new Object();

  /**
   * Should be used within the same process as the lab server. Key: real device serial. Value: local
   * tcp port.
   */
  @GuardedBy("lock")
  private static final Map<String, String> realDeviceProxyMap = new HashMap<>();

  /**
   * Puts a real device serial, tcp address pair into the map.
   *
   * @param serial Android real device serial.
   * @param tcpAddr The local tcp address of the proxy server.
   */
  public static void putRealDeviceProxy(String serial, String tcpAddr) {
    synchronized (lock) {
      if (serial == null || tcpAddr == null) {
        throw new NullPointerException("serial or tcpAddr should not be null.");
      }
      if (realDeviceProxyMap.containsKey(serial) || realDeviceProxyMap.containsValue(tcpAddr)) {
        // Can be caused by unexpected termination of the previous test without clearing the map.
        logger.atSevere().log(
            "serial %s or tcp address %s already exists in the proxy map.", serial, tcpAddr);
      }
      realDeviceProxyMap.put(serial, tcpAddr);
    }
  }

  /**
   * Removes a real device serial from the real device proxy map.
   *
   * @param serial The serial of the real device.
   */
  public static void removeRealDeviceProxy(String serial) {
    synchronized (lock) {
      if (realDeviceProxyMap.containsKey(serial)) {
        realDeviceProxyMap.remove(serial);
      }
    }
  }

  /**
   * Whether the local tcp address is a real device proxy server.
   *
   * @param tcpAddr the local tcp address in the format of localhost:port
   * @return true if the tcpAddr is a real device proxy server, false otherwise.
   */
  public static boolean isRealDeviceProxy(String tcpAddr) {
    synchronized (lock) {
      try {
        return realDeviceProxyMap.containsValue(tcpAddr);
      } catch (NullPointerException e) {
        logger.atWarning().withCause(e).log("The tcp address is null");
      }
      return false;
    }
  }

  /** Clears the realDeviceProxyMap. */
  public static void clearRealDeviceProxy() {
    synchronized (lock) {
      realDeviceProxyMap.clear();
    }
  }

  private AndroidRealDeviceProxyManager() {}
}
