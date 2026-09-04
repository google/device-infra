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

import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;

/**
 * Presentation helper for formatting key display names in host search context.
 *
 * <p>In host search, host fields are the entity's native fields and do NOT carry redundant "Host "
 * prefixes (e.g. "Lab Server Connectivity", "Lab Type", "Daemon Server Status"). Host properties
 * are labeled "Host Property <name>" in title contexts and bare name in table contexts.
 */
public final class HostKeyDisplays {

  /** Standard display name for a key (clean name for tables, column headers). */
  public static String standardDisplayName(HostKeyDescriptor key) {
    return key.display().name();
  }

  /**
   * Title display name for host dialogs and suggestions: clean names for built-in host fields and
   * "Host Property <name>" for discovered host properties.
   */
  public static String titleDisplayName(HostKeyDescriptor key) {
    if (key.isLongTail() && key.id().startsWith(HostKeys.PREFIX_HOST_PROPERTY)) {
      return "Host Property " + key.display().name();
    }
    return key.display().name();
  }

  /** Short chip label for pills: built-in display name for built-ins, bare name for properties. */
  public static String pillKey(HostKeyDescriptor key) {
    return key.display().name();
  }

  private HostKeyDisplays() {}
}
