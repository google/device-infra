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

package com.google.devtools.mobileharness.fe.v6.service.search.index;

import com.google.common.collect.ImmutableSet;

/**
 * The single source of truth for fleet search key ids and their deployment-independent properties.
 *
 * <p>A key id is a namespaced string such as {@code field::status}, {@code dim::pool}, {@code
 * host::host_name}, or {@code config::wifi_ssid}. These ids flow through the whole search stack:
 * the index builder records them, the filter engine matches them, and the cell mapper, value
 * lister, chip resolver, suggester, column cataloger, and group searcher all read them back.
 * Defining each id and each cross-cutting key set once here prevents the silent drift that a
 * mistyped literal in any one of those files would otherwise cause (an empty column, a broken
 * filter) with no compile error.
 *
 * <p>Only facts that are true of a key in every deployment live here (its id, its namespace,
 * whether its values are plural, opaque, multi-valued, or free-form identifiers). Per-scenario
 * curation (which keys to promote, the suggestion priority tiers, the empty-state seed) is
 * deployment dependent and lives behind the scenario curation abstraction instead.
 */
public final class FleetSearchKeys {

  private FleetSearchKeys() {}

  // ---- Namespace prefixes ----

  public static final String FIELD_PREFIX = "field::";
  public static final String DIM_PREFIX = "dim::";
  public static final String PROP_PREFIX = "prop::";
  public static final String HOST_PREFIX = "host::";
  public static final String CONFIG_PREFIX = "config::";

  // ---- Built-in device field keys ----

  public static final String FIELD_UUID = "field::uuid";
  public static final String FIELD_STATUS = "field::status";
  public static final String FIELD_TYPE = "field::type";
  public static final String FIELD_OWNER = "field::owner";
  public static final String FIELD_DRIVER = "field::driver";
  public static final String FIELD_DECORATOR = "field::decorator";
  public static final String FIELD_EXECUTOR = "field::executor";

  // ---- Built-in dimension keys referenced by logic (others are discovered from data) ----

  public static final String DIM_QUARANTINED = "dim::quarantined";

  // ---- Built-in host keys ----

  public static final String HOST_NAME = "host::host_name";
  public static final String HOST_IP = "host::host_ip";
  public static final String HOST_LAB_TYPE = "host::lab_type";
  public static final String HOST_ATS_CONTROLLER = "host::ats_controller";

  // ---- Built-in config keys ----

  public static final String CONFIG_WIFI_SSID = "config::wifi_ssid";

  // ---- Cross-cutting key sets (deployment independent) ----

  /**
   * Keys rendered with a plural label ("Owners", not "Owner"), because a device carries a set of
   * values for them. Used when composing chip and suggestion text.
   */
  public static final ImmutableSet<String> PLURAL_DISPLAY_KEYS =
      ImmutableSet.of(FIELD_OWNER, FIELD_DRIVER, FIELD_DECORATOR, FIELD_EXECUTOR);

  /**
   * Keys whose raw stored value differs from the label an operator sees, so advanced (raw value)
   * matching would confuse rather than help. The value list and cell renderer show the per-value
   * display for these instead of the raw term.
   */
  public static final ImmutableSet<String> VALUE_DISPLAY_KEYS =
      ImmutableSet.of(HOST_ATS_CONTROLLER);

  /**
   * Keys that carry a set of values, so "empty" / "not empty" filters are meaningful. Single-valued
   * keys such as Status never qualify, and raw discovered dimensions are absent by design.
   */
  public static final ImmutableSet<String> MULTI_VALUE_KEYS =
      ImmutableSet.of(
          FIELD_TYPE,
          FIELD_OWNER,
          FIELD_DRIVER,
          FIELD_DECORATOR,
          FIELD_EXECUTOR,
          "dim::os",
          "dim::model",
          "dim::sdk_version",
          "dim::software_version",
          HOST_LAB_TYPE);

  /**
   * Identifier keys whose values are free-form and effectively unique per device (UUIDs, serials,
   * MAC and network addresses), so a value picker with facet counts is pointless. The value list
   * omits these.
   */
  public static final ImmutableSet<String> PLAIN_VALUE_KEYS =
      ImmutableSet.of(
          FIELD_UUID,
          "dim::uuid",
          "dim::id",
          "dim::serial",
          "dim::control_id",
          "dim::mac_address",
          "dim::bluetooth_mac_address",
          "dim::soc_id",
          "dim::network_address",
          "dim::gservices_android_id");

  /**
   * Keys treated as device identifiers when routing a typed query to a key (UUID like or host
   * addressing). This is {@link #PLAIN_VALUE_KEYS} plus the host name and host ip, which the value
   * list still offers a picker for but the suggester routes as identifiers.
   */
  public static final ImmutableSet<String> IDENTIFIER_KEYS =
      ImmutableSet.<String>builder().addAll(PLAIN_VALUE_KEYS).add(HOST_NAME).add(HOST_IP).build();
}
