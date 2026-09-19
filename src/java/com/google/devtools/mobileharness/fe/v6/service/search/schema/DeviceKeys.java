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

package com.google.devtools.mobileharness.fe.v6.service.search.schema;

import com.google.common.collect.ImmutableList;

/**
 * Standard open-source device key descriptors for MobileHarness device search.
 *
 * <p>Contains Group 1 (Universal Common Device Keys: typed {@code DeviceInfo} fields and core
 * dimensions), the Group 1 host keys projected into device search, and Group 2 (Standalone ATS WiFi
 * SSID). Each device-native key declares its {@code GetLabInfo} device sources, which give both the
 * mask contribution and the raw extraction; WiFi SSID has no source because it is fed by
 * ConfigService.
 *
 * <p>This catalog depends on {@link HostKeys}: a host attribute shown in device search is a
 * projected key ({@link #projectHostKey}) that reuses the host key's id and lab sources, adding
 * only a device-search display name. The dependency is one-way (host keys never reference device
 * keys).
 */
public final class DeviceKeys {

  /**
   * Namespace markers of device-search key ids. They exist for the registry's id parsing and for
   * the long-tail minting helpers. A built-in key's id is spelled once, in its descriptor below or
   * in a deployment catalog, and is referenced everywhere else through that descriptor constant or
   * {@link DeviceKeyRegistry#getKey}; no caller combines a prefix with a name to refer to a
   * built-in key.
   */
  public static final String PREFIX_DEVICE_FIELD = "device_field::";

  public static final String PREFIX_DIMENSION = "dimension::";
  public static final String PREFIX_DEVICE_CONFIG = "device_config::";

  // Group 1: Universal common device keys (typed DeviceInfo fields).
  public static final DeviceKeyDescriptor UUID =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DEVICE_FIELD + "uuid")
          .setDeviceInfoSource(
              DeviceInfoSource.field(
                  "device_locator.id", d -> nonEmpty(d.getDeviceLocator().getId())))
          .setDisplay(KeyDisplay.of("UUID"))
          .setAliases("id", "device id", "device uuid")
          .build();

  public static final DeviceKeyDescriptor STATUS =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DEVICE_FIELD + "status")
          .setDeviceInfoSource(
              DeviceInfoSource.field(
                  "device_status", d -> ImmutableList.of(d.getDeviceStatus().name())))
          .setDisplay(KeyDisplay.of("Status"))
          .setAliases("device status")
          .build();

  public static final DeviceKeyDescriptor TYPE =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DEVICE_FIELD + "type")
          .setDeviceInfoSource(
              DeviceInfoSource.field(
                  "device_feature.type",
                  d -> ImmutableList.copyOf(d.getDeviceFeature().getTypeList())))
          .setDisplay(KeyDisplay.of("Type"))
          .setAliases("type(s)", "device type(s)")
          .build();

  public static final DeviceKeyDescriptor DRIVER =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DEVICE_FIELD + "driver")
          .setDeviceInfoSource(
              DeviceInfoSource.field(
                  "device_feature.driver",
                  d -> ImmutableList.copyOf(d.getDeviceFeature().getDriverList())))
          .setDisplay(KeyDisplay.plural("Supported Drivers"))
          .setAliases("driver(s)", "supported driver(s)", "device supported driver(s)")
          .build();

  public static final DeviceKeyDescriptor DECORATOR =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DEVICE_FIELD + "decorator")
          .setDeviceInfoSource(
              DeviceInfoSource.field(
                  "device_feature.decorator",
                  d -> ImmutableList.copyOf(d.getDeviceFeature().getDecoratorList())))
          .setDisplay(KeyDisplay.plural("Supported Decorators"))
          .setAliases("decorator(s)", "supported decorator(s)", "device supported decorator(s)")
          .build();

  // Group 1: Universal common core dimensions. "version" alone is shared; SDK version is declared
  // first in COMMON_DEVICE_KEYS and is therefore the preferred resolution.
  public static final DeviceKeyDescriptor MODEL =
      dimensionKey("model", KeyDisplay.of("Model"), "device model");
  public static final DeviceKeyDescriptor OS = dimensionKey("os", KeyDisplay.of("OS"), "device os");
  public static final DeviceKeyDescriptor SDK_VERSION =
      dimensionKey("sdk_version", KeyDisplay.of("SDK Version"), "version");
  public static final DeviceKeyDescriptor SOFTWARE_VERSION =
      dimensionKey("software_version", KeyDisplay.of("Software Version"), "version");
  public static final DeviceKeyDescriptor DEVICE_FORM =
      dimensionKey("device_form", KeyDisplay.of("Form"), "device form");
  public static final DeviceKeyDescriptor DEVICE_CLASS_NAME =
      dimensionKey(
          "device_class_name", KeyDisplay.of("Device Class"), "class", "device class name");
  public static final DeviceKeyDescriptor MANUFACTURER =
      dimensionKey("manufacturer", KeyDisplay.of("Manufacturer"), "make", "brand");

  /** Standard Group 1 common device-native keys (present in every deployment). */
  public static final ImmutableList<DeviceKeyDescriptor> COMMON_DEVICE_KEYS =
      ImmutableList.of(
          UUID,
          STATUS,
          TYPE,
          DRIVER,
          DECORATOR,
          MODEL,
          OS,
          SDK_VERSION,
          SOFTWARE_VERSION,
          DEVICE_FORM,
          DEVICE_CLASS_NAME,
          MANUFACTURER);

  /**
   * Group 1 host keys projected into device search (cross-entity host attributes stamped onto each
   * device). {@code device_count} is deliberately not projected: it is a host-only numeric key.
   */
  public static final DeviceKeyDescriptor HOST_NAME =
      projectHostKey(HostKeys.HOST_NAME, KeyDisplay.of("Host Name"), "hostname", "host");

  public static final DeviceKeyDescriptor HOST_IP =
      projectHostKey(HostKeys.HOST_IP, KeyDisplay.of("Host IP"), "ip");
  public static final DeviceKeyDescriptor HOST_CONNECTIVITY =
      projectHostKey(
          HostKeys.CONNECTIVITY,
          KeyDisplay.of("Host Lab Server Connectivity"),
          "connectivity",
          "lab server connectivity");
  public static final DeviceKeyDescriptor HOST_OS =
      projectHostKey(HostKeys.HOST_OS, KeyDisplay.of("Host OS"));
  public static final DeviceKeyDescriptor HOST_LAB_SERVER_VERSION =
      projectHostKey(
          HostKeys.LAB_SERVER_VERSION,
          KeyDisplay.of("Host Lab Server Version"),
          "lab server version");

  public static final ImmutableList<DeviceKeyDescriptor> COMMON_HOST_PROJECTIONS =
      ImmutableList.of(HOST_NAME, HOST_IP, HOST_CONNECTIVITY, HOST_OS, HOST_LAB_SERVER_VERSION);

  // The helpers below are package-private by design. They are catalog-authoring helpers shared
  // across the schema catalogs (DeviceKeys, AtsDeviceKeys, InternalDeviceKeys,
  // PartnerAtsDeviceKeys) and the registry. They are deliberately NOT public: the sole public way
  // to obtain a key is through the registry (DeviceKeyRegistry#getKey, #dimensionKey,
  // #hostPropertyKey), so no caller can hand-build or mint a key while bypassing the registry,
  // which is the single key-id authority.

  /**
   * Builds a built-in device key backed by a single named composite dimension, with the words a
   * user may type for it besides its display name.
   */
  static DeviceKeyDescriptor dimensionKey(
      String dimensionName, KeyDisplay display, String... aliases) {
    return DeviceKeyDescriptor.builder()
        .setId(PREFIX_DIMENSION + dimensionName)
        .setDeviceInfoSource(DeviceInfoSource.dimension(dimensionName))
        .setDisplay(display)
        .setAliases(aliases)
        .setIsDimension(true)
        .build();
  }

  /**
   * Builds a long-tail device key for a dimension discovered from data. Package-private on purpose:
   * this is the implementation the registry delegates to. Callers mint long-tail keys through the
   * public {@link DeviceKeyRegistry#dimensionKey(String)} (or {@link
   * DeviceKeyRegistry#getKey(String)}), never this static, so the registry stays the single choke
   * point that stamps {@code isLongTail} and enforces scope. The minted key carries no curated
   * display (raw name) and is flagged long-tail.
   */
  static DeviceKeyDescriptor longTailDimensionKey(String dimensionName) {
    if (dimensionName == null || dimensionName.trim().isEmpty()) {
      throw new IllegalArgumentException("Dimension name cannot be empty");
    }
    return DeviceKeyDescriptor.builder()
        .setId(PREFIX_DIMENSION + dimensionName)
        .setDeviceInfoSource(DeviceInfoSource.dimension(dimensionName))
        .setDisplay(KeyDisplay.of(dimensionName))
        .setIsDimension(true)
        .setIsLongTail(true)
        .build();
  }

  /**
   * Projects a host key into device search: same id and lab sources, a device-search display name
   * and device-search aliases (the host key's own aliases are not inherited, because the same
   * attribute is named differently from a device row, for example {@code "host os"} rather than
   * {@code "os"}). Preserves the host key's long-tail flag so a projected discovered host property
   * stays long-tail.
   */
  static DeviceKeyDescriptor projectHostKey(
      HostKeyDescriptor host, KeyDisplay deviceDisplay, String... deviceAliases) {
    return DeviceKeyDescriptor.builder()
        .setId(host.id())
        .setLabInfoSources(host.labInfoSources())
        .setDisplay(deviceDisplay)
        .setAliases(deviceAliases)
        .setIsLongTail(host.isLongTail())
        .setIsHostProperty(host.isHostProperty())
        .build();
  }

  /**
   * The canonical id for a dimension name discovered from data or typed by a user. This is the only
   * sanctioned way to form a {@code dimension::} id outside a catalog; pass the result to {@link
   * DeviceKeyRegistry#getKey} rather than treating it as a key. A built-in key is referenced by its
   * descriptor constant, never rebuilt from its name.
   */
  public static String dimensionKeyId(String dimensionName) {
    return PREFIX_DIMENSION + dimensionName;
  }

  private static ImmutableList<String> nonEmpty(String value) {
    return value.isEmpty() ? ImmutableList.of() : ImmutableList.of(value);
  }

  private DeviceKeys() {}
}
