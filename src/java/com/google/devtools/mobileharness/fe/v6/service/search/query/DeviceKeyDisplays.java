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

import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;

/**
 * Presentation helper for formatting key display names in device search context.
 *
 * <p>In device search, host fields projected onto devices carry a "Host " prefix to clarify their
 * cross-entity provenance (e.g. "Host Lab Server Connectivity", "Host Lab Type").
 */
public final class DeviceKeyDisplays {

  /** Standard display name for a key (clean name for tables, column headers). */
  public static String standardDisplayName(DeviceKeyDescriptor key) {
    return key.display().name();
  }

  /**
   * Title display name for dialogs and suggestion main texts: prepends "Dimension " / "Host
   * Property " for discovered long-tail keys.
   */
  public static String titleDisplayName(DeviceKeyDescriptor key) {
    if (key.isLongTail()) {
      if (key.id().startsWith(DeviceKeys.PREFIX_DIMENSION)) {
        return "Dimension " + key.display().name();
      }
      if (key.id().startsWith(HostKeys.PREFIX_HOST_PROPERTY)) {
        return "Host Property " + key.display().name();
      }
    }
    return key.display().name();
  }

  /**
   * Short chip label for pills: built-in display name for built-ins, bare name for dimensions, and
   * "Host " prefix for host properties.
   */
  public static String pillKey(DeviceKeyDescriptor key) {
    if (key.isLongTail()) {
      if (key.id().startsWith(DeviceKeys.PREFIX_DIMENSION)) {
        return key.display().name();
      }
      if (key.id().startsWith(HostKeys.PREFIX_HOST_PROPERTY)) {
        return "Host " + key.display().name();
      }
    }
    return key.display().name();
  }

  private DeviceKeyDisplays() {}
}
