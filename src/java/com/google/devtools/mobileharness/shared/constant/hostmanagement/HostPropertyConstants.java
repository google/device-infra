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

package com.google.devtools.mobileharness.shared.constant.hostmanagement;

/** Constants of host properties. */
public final class HostPropertyConstants {

  /** Keys of host properties. */
  public enum HostPropertyKey {
    /** The host name of the host. */
    HOST_NAME,
    /** The IP address of the host. */
    HOST_IP,
    /** The OS type of the host. */
    HOST_OS,
    /** The OS version of the host. */
    HOST_OS_VERSION,
    /** The version of the host. */
    HOST_VERSION,
    /** The Java version of the JVM runned by the host. */
    JAVA_VERSION,
    /** the location of the host. */
    LAB_LOCATION,
    /** The lab type of the host, whose value should be "Core", "Satellite", "SLaaS" or "Fusion". */
    LAB_TYPE,
    /** The type of the location of the host. */
    LOCATION_TYPE,
    /** The total memory of the host. */
    TOTAL_MEM,
    /** The total disk space of the host. */
    ROOT_DISK_SPACE,
  }

  private HostPropertyConstants() {}
}
