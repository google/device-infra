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

package com.google.devtools.mobileharness.fe.v6.service.config.util;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DimensionList;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Shared utility methods and system dimension constants for batch device configuration handlers.
 */
public final class BatchDeviceConfigUtil {

  private static final String DIM_PREFIX = "dim::";
  private static final Pattern VALID_DIMENSION_NAME_PATTERN = Pattern.compile("^[a-z0-9_.\\-]+$");

  /**
   * OmniLab system-detected dimension names populated automatically by Lab Server detectors and
   * system properties. Operators cannot configure these keys manually in DeviceConfig.
   */
  public static final ImmutableSet<String> SYSTEM_DETECTED_DIMENSION_NAMES =
      ImmutableSet.of(
          "abi",
          "abilist",
          "active_modem_count",
          "agsa_version",
          "alert_lab_disk_usable_size",
          "allocation_key",
          "ap_ar_ns",
          "ap_ar_s",
          "api_level",
          "ar_force_update",
          "ar_update_allow",
          "architecture",
          "available_xcode_versions",
          "baseband_version",
          "battery_cycle_count",
          "battery_health",
          "battery_level",
          "battery_status",
          "battery_temperature",
          "blocked_ios_version_upgrade",
          "bluetooth_mac_address",
          "boot_to_vr",
          "brand",
          "build",
          "build_alias",
          "build_type",
          "carrier_ids",
          "characteristics",
          "china_model",
          "chrome_version",
          "cloudrpc_failure",
          "codename",
          "color",
          "communication_type",
          "control_id",
          "cpu_architecture",
          "custom_profile",
          "dab_broker_uri",
          "dab_device_id",
          "ddr_size",
          "ddr_type",
          "ddr_vendor",
          "decorator",
          "device",
          "device_admin_locked",
          "device_admin_wifi_restricted",
          "device_class_name",
          "device_form",
          "device_free_storage",
          "device_free_storage_percentage",
          "device_name",
          "device_priority",
          "device_supports_container",
          "device_supports_moreto",
          "devicetype",
          "devkey_allow",
          "dial_app_url_suffix",
          "dial_rest_url_suffix",
          "disk_type",
          "display_panel_vendor",
          "dm_type",
          "dpm_state",
          "driver",
          "executor",
          "external_storage_status",
          "feature",
          "free_external_storage",
          "free_external_storage_percentage",
          "free_internal_storage",
          "free_internal_storage_percentage",
          "gcs_failure",
          "gms_version",
          "gmscore_signature",
          "gservices_android_id",
          "gsm_operator_alpha",
          "hardware",
          "hardware_sku",
          "hardware_type",
          "hardware_ufs",
          "has_ramdump",
          "hinge_angle",
          "host_identity_status",
          "host_ip",
          "host_name",
          "host_os",
          "host_os_version",
          "host_total_mem",
          "host_version",
          "host_xcode_version",
          "iccid",
          "iccids",
          "id",
          "imei",
          "incremental_build",
          "installed_daemon",
          "internal_storage_status",
          "internet",
          "ios_simulator_id",
          "ip",
          "is_gki_kernel",
          "kernel_release_number",
          "lab_file_system_io_error",
          "lab_location",
          "lab_supports_container",
          "lab_type",
          "language",
          "launcher_1",
          "launcher_3",
          "launcher_gel",
          "locale",
          "location_type",
          "mac_address",
          "machine_hardware_name",
          "machine_type",
          "manufacturer",
          "mcc_mnc",
          "memory_class",
          "model",
          "modem_secure_boot_status",
          "monitored",
          "monsoon_id",
          "monsoon_status",
          "moreto_lab_version",
          "mtaas_device_checkin_group",
          "native_bridge",
          "network_address",
          "network_connection_stability",
          "network_ssid",
          "nonsec_ar",
          "nos_production",
          "num_cpus",
          "oem_unlock",
          "omni_mode_usage",
          "os",
          "os_version_number",
          "owner",
          "pat_connection_mode",
          "pat_device_configured",
          "pat_id",
          "ping_google_stability",
          "ping_time_avg_in_sec",
          "ping_time_max_in_sec",
          "port",
          "power_switch_ip_address",
          "power_switch_mac_address",
          "power_switch_port",
          "power_switch_type",
          "preview_sdk_version",
          "product_board",
          "product_device",
          "product_type",
          "provisioned_by",
          "quarantined",
          "radio_version",
          "reachable_via_dial",
          "reachable_via_ping",
          "reboot_to_recover",
          "recovery_status",
          "region",
          "release_version",
          "release_version_major",
          "revision",
          "rooted",
          "run_target",
          "sbdp_allow",
          "sbdp_ar_check",
          "sbdp_ar_update",
          "screen_density",
          "screen_size",
          "screenshot_able",
          "sdk_full_version",
          "sdk_version",
          "sec_ar",
          "secure_boot",
          "serial",
          "serial_responsive",
          "sign",
          "signal_strengths_in_json",
          "sim_card_info",
          "sim_card_type",
          "sim_infos",
          "sim_operator",
          "sim_operator_alpha",
          "sim_state",
          "soc_id",
          "soc_model",
          "soc_rev",
          "software_version",
          "status",
          "storage_capacity",
          "storage_lifetime_percentage",
          "storage_vendor",
          "supervised",
          "supports_adhoc",
          "supports_gmscore",
          "svelte_device",
          "tenant_config_source_path",
          "total_memory",
          "tradefed_virtual_device_id",
          "type",
          "uuid",
          "uuid_volatile",
          "veritymode",
          "whitelisted_proxy_ctl",
          "wifi",
          "wifi_rssi",
          "wifi_ssid",
          "writable_external_storage",
          "youtube_installed");

  /** Sorted list of system-detected dimension names for proto responses. */
  public static final ImmutableList<String> SORTED_SYSTEM_DETECTED_DIMENSION_NAMES =
      ImmutableList.sortedCopyOf(SYSTEM_DETECTED_DIMENSION_NAMES);

  private BatchDeviceConfigUtil() {}

  /** Normalizes a dimension name by trimming whitespace and stripping any {@code dim::} prefix. */
  public static String cleanDimensionName(String dimensionName) {
    if (dimensionName == null) {
      return "";
    }
    String trimmed = dimensionName.trim();
    if (Ascii.toLowerCase(trimmed).startsWith(DIM_PREFIX)) {
      return trimmed.substring(DIM_PREFIX.length()).trim();
    }
    return trimmed;
  }

  /** Returns true if the dimension name is reserved for OmniLab system-detected dimensions. */
  public static boolean isSystemDetectedDimension(String dimensionName) {
    return SYSTEM_DETECTED_DIMENSION_NAMES.contains(Ascii.toLowerCase(dimensionName));
  }

  /** Returns true if the dimension name has valid characters and is not system-detected. */
  public static boolean isValidConfigurableDimensionKey(String dimensionName) {
    return !dimensionName.isEmpty()
        && VALID_DIMENSION_NAME_PATTERN.matcher(dimensionName).matches()
        && !isSystemDetectedDimension(dimensionName)
        && !dimensionName.equals("subdevice_dimensions");
  }

  /** Returns true when {@code dimensionList} targets {@code required_dimension}. */
  public static boolean isRequiredDimensionList(DimensionList dimensionList) {
    return dimensionList == DimensionList.DIMENSION_LIST_REQUIRED;
  }

  /**
   * Validates the {@code (dimensionName, dimensionList)} target of a batch dimension request.
   *
   * @param dimensionName a name already normalized by {@link #cleanDimensionName}
   * @return the reason the target is invalid, or empty when it is valid
   */
  public static Optional<String> checkDimensionTarget(
      String dimensionName, DimensionList dimensionList) {
    if (dimensionName.isEmpty()) {
      return Optional.of("Dimension name must not be empty.");
    }
    if (isSystemDetectedDimension(dimensionName)) {
      return Optional.of(
          String.format(
              "\"%s\" is automatically detected by OmniLab and cannot be configured manually.",
              dimensionName));
    }
    if (!isValidConfigurableDimensionKey(dimensionName)) {
      return Optional.of("Invalid dimension name: " + dimensionName);
    }
    if (dimensionList != DimensionList.DIMENSION_LIST_SUPPORTED
        && dimensionList != DimensionList.DIMENSION_LIST_REQUIRED) {
      return Optional.of(
          "Dimension list must be DIMENSION_LIST_SUPPORTED or DIMENSION_LIST_REQUIRED.");
    }
    return Optional.empty();
  }

  /**
   * Produces a canonical order-insensitive and case-insensitive key for a list of dimension values.
   */
  public static ImmutableList<String> canonicalizeValues(List<String> values) {
    return values.stream()
        .filter(v -> !v.isEmpty())
        .map(Ascii::toLowerCase)
        .sorted()
        .collect(toImmutableList());
  }

  /** Extracts the dimension values for {@code (dimensionName, dimensionList)} from a config. */
  public static ImmutableList<String> getDimensionValues(
      BasicDeviceConfig basicConfig, String dimensionName, DimensionList dimensionList) {
    return getDimensionValues(basicConfig.getCompositeDimension(), dimensionName, dimensionList);
  }

  /**
   * Extracts the dimension values for {@code (dimensionName, dimensionList)} from a composite
   * dimension.
   */
  public static ImmutableList<String> getDimensionValues(
      DeviceCompositeDimension compositeDimension,
      String dimensionName,
      DimensionList dimensionList) {
    String cleanName = cleanDimensionName(dimensionName);
    if (cleanName.isEmpty()) {
      return ImmutableList.of();
    }
    List<DeviceDimension> dimensions =
        isRequiredDimensionList(dimensionList)
            ? compositeDimension.getRequiredDimensionList()
            : compositeDimension.getSupportedDimensionList();
    return dimensions.stream()
        .filter(d -> d.getName().equals(cleanName) && !d.getValue().isEmpty())
        .map(DeviceDimension::getValue)
        .collect(toImmutableList());
  }

  /**
   * Replaces all entries matching {@code dimensionName} in the target {@code dimensionList} with
   * {@code newValues}, leaving other dimension keys and the other dimension list unchanged.
   */
  public static DeviceCompositeDimension replaceDimensionValues(
      DeviceCompositeDimension compositeDimension,
      String dimensionName,
      DimensionList dimensionList,
      List<String> newValues) {
    String cleanName = cleanDimensionName(dimensionName);
    DeviceCompositeDimension.Builder builder = compositeDimension.toBuilder();
    Stream<DeviceDimension> replacementEntries =
        newValues.stream()
            .filter(v -> !v.isEmpty())
            .map(v -> DeviceDimension.newBuilder().setName(cleanName).setValue(v).build());

    if (isRequiredDimensionList(dimensionList)) {
      ImmutableList<DeviceDimension> updatedRequired =
          Stream.concat(
                  compositeDimension.getRequiredDimensionList().stream()
                      .filter(d -> !d.getName().equals(cleanName)),
                  replacementEntries)
              .collect(toImmutableList());
      builder.clearRequiredDimension().addAllRequiredDimension(updatedRequired);
    } else {
      ImmutableList<DeviceDimension> updatedSupported =
          Stream.concat(
                  compositeDimension.getSupportedDimensionList().stream()
                      .filter(d -> !d.getName().equals(cleanName)),
                  replacementEntries)
              .collect(toImmutableList());
      builder.clearSupportedDimension().addAllSupportedDimension(updatedSupported);
    }
    return builder.build();
  }

  /** Extracts all non-empty device IDs from a {@link GetLabInfoResponse}. */
  public static ImmutableList<String> extractFleetDeviceIds(GetLabInfoResponse response) {
    if (!response.getLabQueryResult().hasLabView()) {
      return ImmutableList.of();
    }
    return response.getLabQueryResult().getLabView().getLabDataList().stream()
        .flatMap(labData -> labData.getDeviceList().getDeviceInfoList().stream())
        .filter(DeviceInfo::hasDeviceLocator)
        .map(deviceInfo -> deviceInfo.getDeviceLocator().getId())
        .filter(id -> !id.isEmpty())
        .collect(toImmutableList());
  }
}
