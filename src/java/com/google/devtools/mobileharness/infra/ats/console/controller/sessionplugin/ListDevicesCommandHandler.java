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

package com.google.devtools.mobileharness.infra.ats.console.controller.sessionplugin;

import static com.google.common.base.Ascii.toUpperCase;
import static com.google.common.collect.Comparators.max;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.CaseFormat;
import com.google.common.base.Enums;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.deviceinfra.ext.devicemanagement.device.platform.android.realdevice.AndroidRealDeviceConstants;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.DeviceDescriptorProto.DeviceDescriptor;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Success;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.ListDevicesCommand;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.base.TableFormatter;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Handler for "list devices" commands. */
class ListDevicesCommandHandler {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableList<String> HEADERS =
      ImmutableList.of("Serial", "State", "Allocation", "Product", "Variant", "Build", "Battery");
  private static final ImmutableList<String> HEADERS_ALL =
      ImmutableList.<String>builder().addAll(HEADERS).add("class", "TestDeviceState").build();
  private static final Comparator<DeviceDescriptor> DEVICE_DESCRIPTOR_COMPARATOR =
      Comparator.comparing(DeviceDescriptor::getAllocationState)
          .thenComparing(DeviceDescriptor::getSerial);
  private static final String NOT_APPLICABLE = "n/a";

  private static final Duration QUERY_DEVICE_TIMEOUT = Duration.ofSeconds(1);

  private final DeviceQuerier deviceQuerier;
  private final AndroidAdbUtil androidAdbUtil;
  private final AndroidAdbInternalUtil androidAdbInternalUtil;
  private final AndroidSystemSettingUtil androidSystemSettingUtil;
  private final ListeningExecutorService threadPool;
  private final Clock clock;

  @Inject
  ListDevicesCommandHandler(
      DeviceQuerier deviceQuerier,
      AndroidAdbUtil androidAdbUtil,
      AndroidAdbInternalUtil androidAdbInternalUtil,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      ListeningExecutorService threadPool,
      Clock clock) {
    this.deviceQuerier = deviceQuerier;
    this.androidAdbUtil = androidAdbUtil;
    this.androidAdbInternalUtil = androidAdbInternalUtil;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.threadPool = threadPool;
    this.clock = clock;
  }

  /**
   * Example output:
   *
   * <pre>{@code
   * Serial State Allocation Product Variant Build Battery
   * abc ONLINE Available bullhead bullhead MTC20K 100
   * }</pre>
   */
  AtsSessionPluginOutput handle(ListDevicesCommand command)
      throws MobileHarnessException, InterruptedException {
    boolean listAllDevices = command.getListAllDevices();
    ImmutableList<String> headers = listAllDevices ? HEADERS_ALL : HEADERS;
    ImmutableList<DeviceDescriptor> devices = listDevices();
    ImmutableList<ImmutableList<String>> table =
        Stream.concat(
                Stream.of(headers),
                devices.stream()
                    .sorted(DEVICE_DESCRIPTOR_COMPARATOR)
                    .map(device -> formatDeviceDescriptor(device, listAllDevices))
                    .flatMap(Optional::stream))
            .collect(toImmutableList());
    String result = TableFormatter.displayTable(table);
    return AtsSessionPluginOutput.newBuilder()
        .setSuccess(Success.newBuilder().setOutputMessage(result))
        .build();
  }

  private ImmutableList<DeviceDescriptor> listDevices()
      throws MobileHarnessException, InterruptedException {
    if (!Flags.instance().detectAdbDevice.getNonNull()) {
      // Can't use status from ADB to accelerate the process.
      DeviceQueryResult deviceQueryResult = queryDevice();
      return deviceQueryResult.getDeviceInfoList().stream()
          .map(deviceInfo -> convertDeviceInfo(deviceInfo, null))
          .collect(toImmutableList());
    }
    Instant listDevicesQueryInstant = clock.instant();
    ListenableFuture<DeviceQueryResult> deviceQueryResultFuture =
        logFailure(
            threadPool.submit(
                threadRenaming(this::queryDevice, () -> "list-device-device-querier")),
            Level.WARNING,
            "Error occurred in device querier of device lister");
    ImmutableMap<String, DeviceDescriptor> deviceInfoFromAdb = queryDeviceInfoFromAdb();

    Duration remainingQueryDuration =
        QUERY_DEVICE_TIMEOUT.minus(Duration.between(listDevicesQueryInstant, clock.instant()));
    remainingQueryDuration = max(remainingQueryDuration, Duration.ZERO);
    DeviceQueryResult deviceQueryResult = null;
    try {
      deviceQueryResult = deviceQueryResultFuture.get(remainingQueryDuration.toSeconds(), SECONDS);
    } catch (ExecutionException | TimeoutException e) {
      logger.atWarning().withCause(e).log(
          "Failed to query device within %s. Going to use status from ADB directly.",
          QUERY_DEVICE_TIMEOUT);
    }

    HashMap<String, DeviceDescriptor> deviceDescriptorMap = new HashMap<>(deviceInfoFromAdb);
    if (deviceQueryResult != null) {
      for (DeviceInfo deviceInfo : deviceQueryResult.getDeviceInfoList()) {
        String serial = deviceInfo.getId();
        if (deviceInfoFromAdb.containsKey(serial)) {
          deviceDescriptorMap.put(
              deviceInfo.getId(), convertDeviceInfo(deviceInfo, deviceInfoFromAdb.get(serial)));
        }
      }
    }

    return ImmutableList.copyOf(deviceDescriptorMap.values());
  }

  /** Queries device info from ADB to be the backup info for devices. */
  private ImmutableMap<String, DeviceDescriptor> queryDeviceInfoFromAdb()
      throws InterruptedException {
    Map<String, DeviceState> deviceState;
    try {
      deviceState = androidAdbInternalUtil.getDeviceSerialsAsMap(QUERY_DEVICE_TIMEOUT);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to query device state from ADB within %s. Going to use status from DeviceManager"
              + " directly.",
          QUERY_DEVICE_TIMEOUT);
      return ImmutableMap.of();
    }

    ImmutableMap.Builder<String, DeviceDescriptor> deviceDescriptorMap = ImmutableMap.builder();

    for (Entry<String, DeviceState> entry : deviceState.entrySet()) {
      String serial = entry.getKey();
      DeviceState state = entry.getValue();
      DeviceDescriptor.Builder builder =
          DeviceDescriptor.newBuilder()
              .setSerial(serial)
              .setDeviceState(convertDeviceStateFromAdb(state));

      // Set default values for device status.
      builder
          .setProduct(NOT_APPLICABLE)
          .setProductVariant(NOT_APPLICABLE)
          .setBuildId(NOT_APPLICABLE)
          .setBatteryLevel(NOT_APPLICABLE);
      // Unsupported fields.
      builder
          .setAllocationState(NOT_APPLICABLE)
          .setDeviceClass(NOT_APPLICABLE)
          .setTestDeviceState(NOT_APPLICABLE)
          .setIsStubDevice(false);

      if (state.equals(DeviceState.DEVICE)) {
        try {
          builder
              .setProduct(androidAdbUtil.getProperty(serial, AndroidProperty.PRODUCT_BOARD))
              .setProductVariant(androidAdbUtil.getProperty(serial, AndroidProperty.DEVICE))
              .setBuildId(androidAdbUtil.getProperty(serial, AndroidProperty.BUILD_ALIAS))
              .setBatteryLevel(Integer.toString(androidSystemSettingUtil.getBatteryLevel(serial)));
        } catch (MobileHarnessException e) {
          logger.atWarning().withCause(e).log(
              "Failed to get info of device - %s from ADB. The device may be offline.", serial);
        }
      }
      deviceDescriptorMap.put(serial, builder.build());
    }
    return deviceDescriptorMap.buildOrThrow();
  }

  // Converts device state from ADB to the one in DeviceDescriptor.
  private static String convertDeviceStateFromAdb(DeviceState state) {
    switch (state) {
      case BOOTLOADER:
        return "BOOTLOADER";
      case DEVICE:
        return "ONLINE";
      case OFFLINE:
        return "OFFLINE";
      case UNAUTHORIZED:
        return "UNAUTHORIZED";
      case RECOVERY:
        return "RECOVERY";
      case SIDELOAD:
        return "SIDELOAD";
      default:
        return NOT_APPLICABLE;
    }
  }

  private DeviceQueryResult queryDevice() throws MobileHarnessException, InterruptedException {
    try {
      return deviceQuerier.queryDevice(DeviceQueryFilter.getDefaultInstance());
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_LIST_DEVICES_QUERY_DEVICE_ERROR, "Failed to query device", e);
    }
  }

  /** Combines {@link DeviceInfo} from DeviceManager and {@link DeviceDescriptor} from ADB. */
  @VisibleForTesting
  static DeviceDescriptor convertDeviceInfo(
      DeviceInfo deviceInfo, @Nullable DeviceDescriptor deviceInfoFromAdb) {
    // Builder with default values from ADB.
    DeviceDescriptor.Builder builder =
        deviceInfoFromAdb == null ? DeviceDescriptor.newBuilder() : deviceInfoFromAdb.toBuilder();
    ImmutableListMultimap<String, String> dimensions =
        deviceInfo.getDimensionList().stream()
            .collect(
                toImmutableListMultimap(
                    DeviceQuery.Dimension::getName, DeviceQuery.Dimension::getValue));

    builder
        .setSerial(deviceInfo.getId())
        .setAllocationState(getAllocationState(deviceInfo.getStatus()));

    String deviceState = getDeviceState(deviceInfo.getTypeList());
    if (!deviceState.equals(NOT_APPLICABLE) || builder.getDeviceState().isEmpty()) {
      builder.setDeviceState(deviceState);
    }

    String product = getDimension(dimensions, Name.PRODUCT_BOARD.lowerCaseName()).orElse("n/a");
    if (!product.equals(NOT_APPLICABLE) || builder.getProduct().isEmpty()) {
      builder.setProduct(product);
    }

    String productVariant = getDimension(dimensions, Name.DEVICE.lowerCaseName()).orElse("n/a");
    if (!productVariant.equals(NOT_APPLICABLE) || builder.getProductVariant().isEmpty()) {
      builder.setProductVariant(productVariant);
    }

    String buildId =
        getDimension(dimensions, Name.BUILD_ALIAS.lowerCaseName())
            .map(Ascii::toUpperCase)
            .orElse(NOT_APPLICABLE);
    if (!buildId.equals(NOT_APPLICABLE) || builder.getBuildId().isEmpty()) {
      builder.setBuildId(buildId);
    }

    String batteryLevel =
        getDimension(dimensions, Name.BATTERY_LEVEL.lowerCaseName()).orElse("n/a");
    if (!batteryLevel.equals(NOT_APPLICABLE) || builder.getBatteryLevel().isEmpty()) {
      builder.setBatteryLevel(batteryLevel);
    }

    return builder
        .setDeviceClass(
            getDimension(dimensions, Name.DEVICE_CLASS_NAME.lowerCaseName()).orElse("n/a"))
        .setTestDeviceState(getTestDeviceState(deviceInfo.getTypeList()))
        .setIsStubDevice(false)
        .build();
  }

  private static String getDeviceState(List<String> deviceTypes) {
    boolean androidOnlineDevice = false;
    boolean androidFastbootDevice = false;
    boolean androidFlashableDevice = false;
    boolean androidOfflineDevice = false;
    boolean androidUnauthorizedDevice = false;
    boolean androidRecoveryDevice = false;
    for (String deviceType : deviceTypes) {
      switch (deviceType) {
        case "AndroidLocalEmulator":
        case AndroidRealDeviceConstants.ANDROID_ONLINE_DEVICE:
          androidOnlineDevice = true;
          break;
        case AndroidRealDeviceConstants.ANDROID_FASTBOOT_DEVICE:
          androidFastbootDevice = true;
          break;
        case AndroidRealDeviceConstants.ANDROID_FLASHABLE_DEVICE:
          androidFlashableDevice = true;
          break;
        case "AndroidOfflineDevice":
          androidOfflineDevice = true;
          break;
        case "AndroidUnauthorizedDevice":
          androidUnauthorizedDevice = true;
          break;
        case AndroidRealDeviceConstants.ANDROID_RECOVERY_DEVICE:
          androidRecoveryDevice = true;
          break;
        default:
          break;
      }
    }
    if (androidOnlineDevice) {
      return "ONLINE";
    }
    if (androidOfflineDevice) {
      return "OFFLINE";
    }
    if (androidUnauthorizedDevice) {
      return "UNAUTHORIZED";
    }
    if (androidRecoveryDevice) {
      return "RECOVERY";
    }
    if (!androidFastbootDevice && androidFlashableDevice) {
      return "FASTBOOTD";
    }
    return NOT_APPLICABLE;
  }

  private static String getTestDeviceState(List<String> deviceTypes) {
    boolean androidOnlineDevice = false;
    boolean androidFastbootDevice = false;
    for (String deviceType : deviceTypes) {
      switch (deviceType) {
        case "AndroidLocalEmulator":
        case AndroidRealDeviceConstants.ANDROID_ONLINE_DEVICE:
          androidOnlineDevice = true;
          break;
        case AndroidRealDeviceConstants.ANDROID_FASTBOOT_DEVICE:
          androidFastbootDevice = true;
          break;
        default:
          break;
      }
    }
    if (androidOnlineDevice) {
      return "ONLINE";
    }
    if (androidFastbootDevice) {
      return "FASTBOOT";
    }
    return "NOT_AVAILABLE";
  }

  private static String getAllocationState(String deviceStatus) {
    Optional<DeviceStatus> statusEnum =
        Enums.getIfPresent(DeviceStatus.class, toUpperCase(deviceStatus)).toJavaUtil();
    if (statusEnum.isPresent()) {
      switch (statusEnum.get()) {
        case IDLE:
          return "Available";
        case BUSY:
          return "Allocated";
        case INIT:
        case PREPPING:
        case DYING:
          return "Checking_Availability";
        case LAMEDUCK:
          return "Unavailable";
        default:
      }
    }
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, deviceStatus);
  }

  private static Optional<String> getDimension(
      ImmutableListMultimap<String, String> dimensions, String name) {
    ImmutableList<String> values = dimensions.get(name);
    return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
  }

  /** See com.android.tradefed.device.DeviceManager. */
  private static Optional<ImmutableList<String>> formatDeviceDescriptor(
      DeviceDescriptor deviceDescriptor, boolean listAllDevices) {
    if (!listAllDevices
        && deviceDescriptor.getIsStubDevice()
        && !deviceDescriptor.getAllocationState().equals("Allocated")) {
      return Optional.empty();
    }
    ImmutableList.Builder<String> result = ImmutableList.builder();
    String serial =
        deviceDescriptor.getDisplaySerial().isEmpty()
            ? deviceDescriptor.getSerial()
            : deviceDescriptor.getDisplaySerial();
    result.add(
        serial,
        deviceDescriptor.getDeviceState(),
        deviceDescriptor.getAllocationState(),
        deviceDescriptor.getProduct(),
        deviceDescriptor.getProductVariant(),
        deviceDescriptor.getBuildId(),
        deviceDescriptor.getBatteryLevel());
    if (listAllDevices) {
      result.add(deviceDescriptor.getDeviceClass(), deviceDescriptor.getTestDeviceState());
    }
    return Optional.of(result.build());
  }
}
