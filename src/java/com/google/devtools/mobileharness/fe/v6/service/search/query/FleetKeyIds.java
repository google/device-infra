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

package com.google.devtools.mobileharness.fe.v6.service.search.query;

/** Utility methods for query-layer key identifiers and namespaces. */
public final class FleetKeyIds {

  /**
   * Returns the bare name of a namespaced key by stripping its namespace prefix.
   *
   * <p>For example:
   *
   * <ul>
   *   <li>{@code "device_field::status"} -> {@code "status"}
   *   <li>{@code "dimension::model"} -> {@code "model"}
   *   <li>{@code "host_field::host_name"} -> {@code "host_name"}
   *   <li>{@code "host_property::rack"} -> {@code "rack"}
   * </ul>
   */
  public static String bareName(String keyId) {
    int separator = keyId.lastIndexOf("::");
    return separator >= 0 ? keyId.substring(separator + 2) : keyId;
  }

  private FleetKeyIds() {}
}
