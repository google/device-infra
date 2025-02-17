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

package com.google.devtools.mobileharness.shared.labinfo;

import static com.google.common.collect.ImmutableList.sortedCopyOf;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Maps.immutableEntry;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.collectingAndThen;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.math.IntMath;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroup;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroupKey;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroupKey.HasDimensionValue;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroupKey.HasDimensionValue.NoDimensionValue;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroupResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.GroupedDevices;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest.DeviceGroupCondition;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest.DeviceGroupOperation;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Order.DeviceOrder;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Order.LabOrder;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.DeviceView;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.Page;
import com.google.devtools.mobileharness.shared.util.filter.MaskUtils;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/** Utilities for doing lab query. */
public class LabQueryUtils {

  public static LabQueryResult createNewLabQueryResult(
      LabQuery query, LabInfoProvider labInfoProvider) throws MobileHarnessException {
    LabQueryResult.Builder result =
        LabQueryResult.newBuilder().setTimestamp(TimeUtils.toProtoTimestamp(Instant.now()));

    // Gets filtered lab/device info from device manager.
    LabView rawLabView = labInfoProvider.getLabInfos(query.getFilter());

    // Sets lab view / device view, sorts all LabInfo/DeviceInfo in it.
    setViewAndSort(result, rawLabView, query);

    // Groups devices if necessary, handles device limit in group and group limit.
    groupDevice(result, query.getDeviceViewRequest());

    // Removes fields and dimensions from all LabInfo/DeviceInfo if necessary.
    applyMask(result, query.getMask());

    return result.build();
  }

  /** Sets lab view or device view, and orders labs / devices. */
  private static void setViewAndSort(
      LabQueryResult.Builder result, LabView rawLabView, LabQuery query) {
    // Creates LabInfo/DeviceInfo comparators.
    LabInfoComparator labInfoComparator = new LabInfoComparator(query.getOrder().getLabOrder());
    DeviceInfoComparator deviceInfoComparator =
        new DeviceInfoComparator(query.getOrder().getDeviceOrder());

    if (query.hasDeviceViewRequest()) {
      result.setDeviceView(
          DeviceView.newBuilder()
              .setGroupedDevices(
                  GroupedDevices.newBuilder()
                      .setDeviceList(
                          // Flattens all DeviceInfo and sorts them.
                          rawLabView.getLabDataList().stream()
                              .map(LabData::getDeviceList)
                              .map(DeviceList::getDeviceInfoList)
                              .flatMap(Collection::stream)
                              .sorted(deviceInfoComparator)
                              .collect(
                                  collectingAndThen(
                                      toImmutableList(),
                                      (Function<ImmutableList<DeviceInfo>, DeviceList.Builder>)
                                          flattenedDevices ->
                                              DeviceList.newBuilder()
                                                  .setDeviceTotalCount(flattenedDevices.size())
                                                  .addAllDeviceInfo(flattenedDevices))))));
    } else {
      // Sorts LabInfo/DeviceInfo in lab view.
      LabView.Builder labViewBuilder = rawLabView.toBuilder();
      List<LabData> labDataList = labViewBuilder.getLabDataList();
      labViewBuilder
          .clearLabData()
          .addAllLabData(
              labDataList.stream()
                  .sorted(comparing(LabData::getLabInfo, labInfoComparator))
                  .map(labData -> sortDeviceListInLabData(labData, deviceInfoComparator))
                  .collect(toImmutableList()));
      result.setLabView(labViewBuilder);
    }
  }

  /**
   * Sorts the {@link DeviceInfo} list in a {@link LabData} by the given {@link
   * DeviceInfoComparator}.
   */
  public static LabData sortDeviceListInLabData(
      LabData labData, DeviceInfoComparator deviceInfoComparator) {
    LabData.Builder result = labData.toBuilder();
    DeviceList.Builder deviceListBuilder = result.getDeviceListBuilder();
    List<DeviceInfo> deviceInfoList = deviceListBuilder.getDeviceInfoList();
    deviceListBuilder
        .clearDeviceInfo()
        .addAllDeviceInfo(sortedCopyOf(deviceInfoComparator, deviceInfoList));
    return result.build();
  }

  /** Groups devices, and removes devices from each group by device_limit. */
  private static void groupDevice(
      LabQueryResult.Builder result, DeviceViewRequest deviceViewRequest) {
    if (!result.hasDeviceView()) {
      return;
    }

    // Normalizes device limit.
    int deviceLimit = deviceViewRequest.getDeviceLimit();
    if (deviceLimit < 0) {
      deviceLimit = 0;
    }

    // Traverses all group operations and constructs a device group tree recursively.
    doGroupOperations(
        result.getDeviceViewBuilder().getGroupedDevicesBuilder(),
        deviceViewRequest.getDeviceGroupOperationList(),
        /* groupOperationIndex= */ 0,
        /* deviceLimit= */ deviceLimit);
  }

  /**
   * Grouped a {@linkplain GroupedDevices.Builder node} which contains a {@linkplain
   * GroupedDevices.Builder#getDeviceListBuilder() device list}, by the group operation "{@code
   * groupOperations.get(groupOperationIndex)}" (if available), and then for each {@linkplain
   * GroupedDevices.Builder child node} in the group operation's outcome, this method will be
   * invoked recursively with "{@code groupOperationIndex + 1}".
   *
   * <p>Invalid or empty group operations will be skipped (e.g., an operation with an unrecognized
   * group condition).
   */
  public static void doGroupOperations(
      GroupedDevices.Builder groupedDevicesBuilder,
      List<DeviceGroupOperation> groupOperations,
      int groupOperationIndex,
      int deviceLimit) {
    // Skips empty group operations.
    while (groupOperationIndex < groupOperations.size()
        && groupOperations
            .get(groupOperationIndex)
            .equals(DeviceGroupOperation.getDefaultInstance())) {
      groupOperationIndex++;
    }

    DeviceList.Builder deviceListBuilder = groupedDevicesBuilder.getDeviceListBuilder();
    List<DeviceInfo> deviceInfoList = deviceListBuilder.getDeviceInfoList();

    // If no more group operation, returns immediately.
    if (groupOperationIndex >= groupOperations.size()) {
      // Purges DeviceInfo from DeviceList in accordance with device limit.
      if (deviceLimit > 0) {
        deviceListBuilder
            .clearDeviceInfo()
            .addAllDeviceInfo(
                subList(
                    deviceInfoList, Page.newBuilder().setOffset(0).setLimit(deviceLimit).build()));
      }
      return;
    }

    DeviceGroupOperation deviceGroupOperation = groupOperations.get(groupOperationIndex);

    // Applies one group operation.
    Optional<DeviceGroupOperationResult> deviceGroupOperationResultOptional =
        doOneGroupOperation(deviceInfoList, deviceGroupOperation);

    // If the operation failed (e.g., due to an unrecognized group condition), skips the operation.
    if (deviceGroupOperationResultOptional.isEmpty()) {
      doGroupOperations(
          groupedDevicesBuilder, groupOperations, groupOperationIndex + 1, deviceLimit);
      return;
    }

    // If the operation succeeded.
    DeviceGroupOperationResult deviceGroupOperationResult =
        deviceGroupOperationResultOptional.get();
    DeviceGroupResult.Builder deviceGroupResultBuilder =
        DeviceGroupResult.newBuilder()
            .setDeviceGroupOperation(deviceGroupOperation)
            .setDeviceGroupTotalCount(deviceGroupOperationResult.deviceGroupTotalCount());

    // Recursively applies the remaining group operations to the child nodes.
    for (DeviceGroup.Builder deviceGroupBuilder : deviceGroupOperationResult.deviceGroups()) {
      doGroupOperations(
          deviceGroupBuilder.getGroupedDevicesBuilder(),
          groupOperations,
          groupOperationIndex + 1,
          deviceLimit);
    }

    // Adds all grouped child nodes to group result.
    deviceGroupResultBuilder.addAllDeviceGroup(
        deviceGroupOperationResult.deviceGroups().stream()
            .map(DeviceGroup.Builder::build)
            .collect(toImmutableList()));
    groupedDevicesBuilder.clearDeviceList().setDeviceGroupResult(deviceGroupResultBuilder);
  }

  /**
   * Applies one group operation to a {@link DeviceInfo} list.
   *
   * <p>Returns empty if the operation failed (e.g., due to an unrecognized group condition).
   */
  public static Optional<DeviceGroupOperationResult> doOneGroupOperation(
      List<DeviceInfo> deviceInfoList, DeviceGroupOperation deviceGroupOperation) {
    DeviceGroupCondition deviceGroupCondition = deviceGroupOperation.getDeviceGroupCondition();
    if (deviceGroupCondition.hasSingleDimensionValue()) {
      // Groups devices by "has single dimension value".
      String dimensionName = deviceGroupCondition.getSingleDimensionValue().getDimensionName();

      ImmutableMap<Optional<String>, DeviceGroup.Builder> allDeviceGroupsByDimensionValue =
          Stream.<Entry<Optional<String>, DeviceGroup.Builder>>concat(
                  // Adds a device group representing a device doesn't have the dimension.
                  Stream.of(
                      immutableEntry(
                          Optional.empty(),
                          DeviceGroup.newBuilder()
                              .setDeviceGroupKey(
                                  DeviceGroupKey.newBuilder()
                                      .setHasDimensionValue(
                                          HasDimensionValue.newBuilder()
                                              .setDimensionName(dimensionName)
                                              .setNoDimensionValue(
                                                  NoDimensionValue.getDefaultInstance()))))),
                  // Adds device group for each distinct dimension value, sorted by dimension name.
                  deviceInfoList.stream()
                      .flatMap(deviceInfo -> getDimensionValues(deviceInfo, dimensionName))
                      .distinct()
                      .sorted()
                      .map(
                          dimensionValue ->
                              immutableEntry(
                                  Optional.of(dimensionValue),
                                  DeviceGroup.newBuilder()
                                      .setDeviceGroupKey(
                                          DeviceGroupKey.newBuilder()
                                              .setHasDimensionValue(
                                                  HasDimensionValue.newBuilder()
                                                      .setDimensionName(dimensionName)
                                                      .setDimensionValue(dimensionValue))))))
              .collect(toImmutableMap(Entry::getKey, Entry::getValue));
      int deviceGroupTotalCount = allDeviceGroupsByDimensionValue.size();

      // Handles group limit.
      int groupLimit = deviceGroupOperation.getGroupLimit();
      if (groupLimit < 0) {
        groupLimit = 0;
      }
      ImmutableMap<Optional<String>, DeviceGroup.Builder> deviceGroupsByDimensionValue;
      if (groupLimit > 0) {
        // Purges groups in accordance with group limit.
        deviceGroupsByDimensionValue =
            allDeviceGroupsByDimensionValue.entrySet().stream()
                .limit(groupLimit)
                .collect(toImmutableMap(Entry::getKey, Entry::getValue));
      } else {
        deviceGroupsByDimensionValue = allDeviceGroupsByDimensionValue;
      }

      // Adds DeviceInfo to all corresponding groups (some may already be purged), keeps the order.
      for (DeviceInfo deviceInfo : deviceInfoList) {
        ImmutableSet<String> dimensionValues =
            getDimensionValues(deviceInfo, dimensionName).collect(toImmutableSet());

        Stream.concat(
                dimensionValues.stream()
                    .flatMap(
                        dimensionValue ->
                            Stream.ofNullable(
                                deviceGroupsByDimensionValue.get(Optional.of(dimensionValue)))),
                // If the device doesn't have the dimension, adds it to the "no dimension" group.
                dimensionValues.isEmpty()
                    ? Stream.ofNullable(deviceGroupsByDimensionValue.get(Optional.<String>empty()))
                    : Stream.empty())
            .forEach(
                deviceGroupBuilder ->
                    deviceGroupBuilder
                        .getGroupedDevicesBuilder()
                        .getDeviceListBuilder()
                        .addDeviceInfo(deviceInfo));
      }

      // Sets device total count.
      for (DeviceGroup.Builder deviceGroupBuilder : deviceGroupsByDimensionValue.values()) {
        DeviceList.Builder deviceListBuilder =
            deviceGroupBuilder.getGroupedDevicesBuilder().getDeviceListBuilder();
        deviceListBuilder.setDeviceTotalCount(deviceListBuilder.getDeviceInfoCount());
      }

      return Optional.of(
          DeviceGroupOperationResult.of(
              deviceGroupTotalCount, ImmutableList.copyOf(deviceGroupsByDimensionValue.values())));
    } else {
      // In all other unhandled cases, returns a failure.
      return Optional.empty();
    }
  }

  /**
   * Returns an unsorted and unfiltered list of supported and required dimensions values for the
   * given dimension name.
   */
  private static Stream<String> getDimensionValues(DeviceInfo deviceInfo, String dimensionName) {
    DeviceCompositeDimension compositeDimension =
        deviceInfo.getDeviceFeature().getCompositeDimension();
    return Stream.concat(
        compositeDimension.getSupportedDimensionList().stream()
            .filter(dimension -> dimension.getName().equals(dimensionName))
            .map(DeviceDimension::getValue),
        compositeDimension.getRequiredDimensionList().stream()
            .filter(dimension -> dimension.getName().equals(dimensionName))
            .map(DeviceDimension::getValue));
  }

  /** Applies the given {@link Mask} to the {@link LabQueryResult}. */
  private static void applyMask(LabQueryResult.Builder result, Mask mask) {
    MaskUtils.trimLabQueryResult(result, mask);
  }

  /** Gets a page of the result. */
  public static LabQueryResult getPagedResult(LabQueryResult result, Page normalizedPage) {
    if (normalizedPage.getOffset() == 0 && normalizedPage.getLimit() == 0) {
      return result;
    }

    LabQueryResult.Builder resultBuilder = result.toBuilder();
    if (resultBuilder.hasLabView()) {
      // Handles LabView.
      LabView.Builder labViewBuilder = resultBuilder.getLabViewBuilder();
      List<LabData> labDataList = labViewBuilder.getLabDataList();
      labViewBuilder.clearLabData().addAllLabData(subList(labDataList, normalizedPage));

    } else {
      // Handles DeviceView.
      GroupedDevices.Builder groupedDevicesBuilder =
          resultBuilder.getDeviceViewBuilder().getGroupedDevicesBuilder();
      if (groupedDevicesBuilder.hasDeviceList()) {
        // Handles DeviceList.
        DeviceList.Builder deviceListBuilder = groupedDevicesBuilder.getDeviceListBuilder();
        List<DeviceInfo> deviceInfoList = deviceListBuilder.getDeviceInfoList();
        deviceListBuilder
            .clearDeviceInfo()
            .addAllDeviceInfo(subList(deviceInfoList, normalizedPage));

      } else {
        // Handles DeviceGroupResult.
        DeviceGroupResult.Builder deviceGroupResultBuilder =
            groupedDevicesBuilder.getDeviceGroupResultBuilder();
        List<DeviceGroup> deviceGroupList = deviceGroupResultBuilder.getDeviceGroupList();
        deviceGroupResultBuilder
            .clearDeviceGroup()
            .addAllDeviceGroup(subList(deviceGroupList, normalizedPage));
      }
    }

    return resultBuilder.build();
  }

  /** Resets the offset and limit for a {@link Page} to 0 if they are negative. */
  public static Page normalizePage(Page page) {
    int offset = page.getOffset();
    int limit = page.getLimit();
    if (offset < 0 || limit < 0) {
      return Page.newBuilder().setOffset(max(offset, 0)).setLimit(max(limit, 0)).build();
    } else {
      return page;
    }
  }

  /** Gets a sub list based on a {@linkplain #normalizePage normalized} {@link Page}. */
  private static <T> List<T> subList(List<T> list, Page normalizedPage) {
    int nonnegativeOffset = normalizedPage.getOffset();
    int nonnegativeLimit = normalizedPage.getLimit();
    int toIndex =
        nonnegativeLimit == 0
            ? list.size()
            : min(IntMath.saturatedAdd(nonnegativeOffset, nonnegativeLimit), list.size());
    int fromIndex = min(nonnegativeOffset, toIndex);
    return list.subList(fromIndex, toIndex);
  }

  @AutoValue
  abstract static class DeviceGroupOperationResult {

    abstract int deviceGroupTotalCount();

    abstract ImmutableList<DeviceGroup.Builder> deviceGroups();

    private static DeviceGroupOperationResult of(
        int deviceGroupTotalCount, ImmutableList<DeviceGroup.Builder> deviceGroups) {
      return new AutoValue_LabQueryUtils_DeviceGroupOperationResult(
          deviceGroupTotalCount, deviceGroups);
    }
  }

  private static class DeviceInfoComparator implements Comparator<DeviceInfo> {

    private static final Comparator<DeviceInfo> UUID_COMPARATOR =
        comparing(deviceInfo -> deviceInfo.getDeviceLocator().getId());

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final DeviceOrder deviceOrder;

    private DeviceInfoComparator(DeviceOrder deviceOrder) {
      this.deviceOrder = deviceOrder;
    }

    @Override
    public int compare(DeviceInfo deviceInfo1, DeviceInfo deviceInfo2) {
      // TODO: Sorts devices by more orders.
      return UUID_COMPARATOR.compare(deviceInfo1, deviceInfo2);
    }
  }

  private static class LabInfoComparator implements Comparator<LabInfo> {

    private static final Comparator<LabInfo> HOSTNAME_COMPARATOR =
        comparing(labInfo -> labInfo.getLabLocator().getHostName());

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final LabOrder labOrder;

    private LabInfoComparator(LabOrder labOrder) {
      this.labOrder = labOrder;
    }

    @Override
    public int compare(LabInfo labInfo1, LabInfo labInfo2) {
      // TODO: Sorts labs by more orders.
      return HOSTNAME_COMPARATOR.compare(labInfo1, labInfo2);
    }
  }

  private LabQueryUtils() {}
}
