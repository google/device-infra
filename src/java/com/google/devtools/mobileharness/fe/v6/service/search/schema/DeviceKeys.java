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
          .build();

  public static final DeviceKeyDescriptor STATUS =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DEVICE_FIELD + "status")
          .setDeviceInfoSource(
              DeviceInfoSource.field(
                  "device_status", d -> ImmutableList.of(d.getDeviceStatus().name())))
          .setDisplay(KeyDisplay.of("Status"))
          .build();

  public static final DeviceKeyDescriptor TYPE =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DEVICE_FIELD + "type")
          .setDeviceInfoSource(
              DeviceInfoSource.field(
                  "device_feature.type",
                  d -> ImmutableList.copyOf(d.getDeviceFeature().getTypeList())))
          .setDisplay(KeyDisplay.of("Type"))
          .build();

  public static final DeviceKeyDescriptor DRIVER =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DEVICE_FIELD + "driver")
          .setDeviceInfoSource(
              DeviceInfoSource.field(
                  "device_feature.driver",
                  d -> ImmutableList.copyOf(d.getDeviceFeature().getDriverList())))
          .setDisplay(KeyDisplay.plural("Supported Drivers"))
          .build();

  public static final DeviceKeyDescriptor DECORATOR =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DEVICE_FIELD + "decorator")
          .setDeviceInfoSource(
              DeviceInfoSource.field(
                  "device_feature.decorator",
                  d -> ImmutableList.copyOf(d.getDeviceFeature().getDecoratorList())))
          .setDisplay(KeyDisplay.plural("Supported Decorators"))
          .build();

  // Group 1: Universal common core dimensions.
  public static final DeviceKeyDescriptor MODEL = dimensionKey("model", KeyDisplay.of("Model"));
  public static final DeviceKeyDescriptor OS = dimensionKey("os", KeyDisplay.of("OS"));
  public static final DeviceKeyDescriptor SDK_VERSION =
      dimensionKey("sdk_version", KeyDisplay.of("SDK Version"));
  public static final DeviceKeyDescriptor SOFTWARE_VERSION =
      dimensionKey("software_version", KeyDisplay.of("Software Version"));
  public static final DeviceKeyDescriptor DEVICE_FORM =
      dimensionKey("device_form", KeyDisplay.of("Form"));
  public static final DeviceKeyDescriptor DEVICE_CLASS_NAME =
      dimensionKey("device_class_name", KeyDisplay.of("Device Class"));
  public static final DeviceKeyDescriptor MANUFACTURER =
      dimensionKey("manufacturer", KeyDisplay.of("Manufacturer"));

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
      projectHostKey(HostKeys.HOST_NAME, KeyDisplay.of("Host Name"));

  public static final DeviceKeyDescriptor HOST_IP =
      projectHostKey(HostKeys.HOST_IP, KeyDisplay.of("Host IP"));
  public static final DeviceKeyDescriptor HOST_CONNECTIVITY =
      projectHostKey(HostKeys.CONNECTIVITY, KeyDisplay.of("Host Lab Server Connectivity"));
  public static final DeviceKeyDescriptor HOST_OS =
      projectHostKey(HostKeys.HOST_OS, KeyDisplay.of("Host OS"));
  public static final DeviceKeyDescriptor HOST_LAB_SERVER_VERSION =
      projectHostKey(HostKeys.LAB_SERVER_VERSION, KeyDisplay.of("Host Lab Server Version"));

  public static final ImmutableList<DeviceKeyDescriptor> COMMON_HOST_PROJECTIONS =
      ImmutableList.of(HOST_NAME, HOST_IP, HOST_CONNECTIVITY, HOST_OS, HOST_LAB_SERVER_VERSION);

  // The helpers below are package-private by design. They are catalog-authoring helpers shared
  // across the schema catalogs (DeviceKeys, AtsDeviceKeys, InternalDeviceKeys,
  // PartnerAtsDeviceKeys)
  // and the registry. They are deliberately NOT public: the sole public way to obtain a key is
  // through the registry (DeviceKeyRegistry#getKey / #dimensionKey / #hostPropertyKey), so no
  // caller
  // can hand-build or mint a key while bypassing the registry, which is the single key-id
  // authority.

  /** Builds a built-in device key backed by a single named composite dimension. */
  static DeviceKeyDescriptor dimensionKey(String dimensionName, KeyDisplay display) {
    return DeviceKeyDescriptor.builder()
        .setId(PREFIX_DIMENSION + dimensionName)
        .setDeviceInfoSource(DeviceInfoSource.dimension(dimensionName))
        .setDisplay(display)
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
        .setIsLongTail(true)
        .build();
  }

  /**
   * Projects a host key into device search: same id and lab sources, a device-search display name.
   * Preserves the host key's long-tail flag so a projected discovered host property stays
   * long-tail.
   */
  static DeviceKeyDescriptor projectHostKey(HostKeyDescriptor host, KeyDisplay deviceDisplay) {
    return DeviceKeyDescriptor.builder()
        .setId(host.id())
        .setLabInfoSources(host.labInfoSources())
        .setDisplay(deviceDisplay)
        .setIsLongTail(host.isLongTail())
        .build();
  }

  private static ImmutableList<String> nonEmpty(String value) {
    return value.isEmpty() ? ImmutableList.of() : ImmutableList.of(value);
  }

  private DeviceKeys() {}
}
