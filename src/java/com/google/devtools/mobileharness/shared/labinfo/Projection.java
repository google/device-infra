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

import static com.google.common.base.Ascii.toLowerCase;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.devtools.mobileharness.shared.labinfo.LabQueryUtils.DEVICE_SORT_KEY_PATH;
import static com.google.devtools.mobileharness.shared.labinfo.LabQueryUtils.LAB_SORT_KEY_PATH;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroup;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.GroupedDevices;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest.DeviceGroupCondition;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest.DeviceGroupOperation;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask.DimensionsMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.shared.util.filter.CompiledDeviceInfoMask;
import com.google.devtools.mobileharness.shared.util.filter.CompiledLabInfoMask;
import com.google.devtools.mobileharness.shared.util.filter.DimensionNameMatcher;
import com.google.devtools.mobileharness.shared.util.filter.MaskUtils;
import com.google.protobuf.FieldMask;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * What a lab query builds and what it hands back.
 *
 * <p>A {@link LabQuery.Mask} says which fields the client wants. Serving the query needs slightly
 * more than that: ordering labs reads {@code lab_locator.host_name}, ordering devices reads {@code
 * device_locator.id}, and each {@code device_view_request} group-by reads one more field or
 * dimension. A {@code Projection} therefore holds two things:
 *
 * <ul>
 *   <li>the <b>push-down masks</b>, {@link #labInfoMask} and {@link #deviceInfoMask}: the client
 *       mask widened with every field and dimension that ordering and grouping read, compiled so
 *       the {@link LabInfoProvider} builds only those fields;
 *   <li>the <b>widening</b>, {@link #widening}: exactly which fields and dimension names were added
 *       on top of the client mask, so that {@link #undoWidening} can remove them once ordering and
 *       grouping are done and the client receives exactly what it asked for.
 * </ul>
 *
 * <p>{@link LabQueryUtils#createNewLabQueryResult} is the only intended caller:
 *
 * <pre>{@code
 * Projection projection = Projection.of(query);
 * LabView raw = provider.getLabInfos(filter, projection.labInfoMask(), projection.deviceInfoMask());
 * ... order and group ...
 * projection.undoWidening(result);
 * }</pre>
 *
 * <p>The widening rules mirror what {@link LabQueryUtils} reads. A mask half that keeps everything
 * (no {@code field_mask}) or drops everything (empty {@code field_mask}) is never widened: the
 * first already contains the sort keys, the second builds nothing to sort. Undoing is surgical: a
 * field is added only when the client requested nothing under it, so clearing that field afterwards
 * restores the client mask exactly, without a reflective {@code FieldMaskUtil.trim} per message.
 *
 * @param labInfoMask push-down mask for {@code LabInfo}
 * @param deviceInfoMask push-down mask for {@code DeviceInfo}
 * @param widening what the push-down masks contain beyond the client mask
 */
public record Projection(
    CompiledLabInfoMask labInfoMask, CompiledDeviceInfoMask deviceInfoMask, Widening widening) {

  private static final String LAB_LOCATOR_PATH = "lab_locator";
  private static final String DEVICE_LOCATOR_PATH = "device_locator";
  private static final String DEVICE_STATUS_PATH = "device_status";
  private static final String DEVICE_FEATURE_PATH = "device_feature";

  /** The {@code device_feature} sub-fields a group-by can add, each knowing how to clear itself. */
  enum WidenedFeatureField {
    COMPOSITE_DIMENSION("device_feature.composite_dimension") {
      @Override
      void clear(DeviceFeature.Builder feature) {
        feature.clearCompositeDimension();
      }
    },
    TYPE("device_feature.type") {
      @Override
      void clear(DeviceFeature.Builder feature) {
        feature.clearType();
      }
    },
    OWNER("device_feature.owner") {
      @Override
      void clear(DeviceFeature.Builder feature) {
        feature.clearOwner();
      }
    },
    EXECUTOR("device_feature.executor") {
      @Override
      void clear(DeviceFeature.Builder feature) {
        feature.clearExecutor();
      }
    };

    private static final ImmutableSet<WidenedFeatureField> ALL = ImmutableSet.copyOf(values());

    final String path;

    WidenedFeatureField(String path) {
      this.path = path;
    }

    abstract void clear(DeviceFeature.Builder feature);
  }

  /**
   * How a sort key that ordering added is undone: by clearing the key alone when the client asked
   * for sibling fields of the same locator, or the whole locator when the client asked for nothing
   * under it and the locator exists only because of the key.
   */
  public enum AddedSortKey {
    NOT_ADDED,
    CLEAR_KEY,
    CLEAR_LOCATOR
  }

  /**
   * What the push-down masks contain beyond the client mask.
   *
   * @param labHostName whether and how {@code lab_locator.host_name} was added
   * @param deviceId whether and how {@code device_locator.id} was added
   * @param deviceStatus {@code device_status} was added
   * @param deviceFeature {@code device_feature} itself was not requested and is present only
   *     because of {@code featureFields}
   * @param featureFields the {@code device_feature} sub-fields that were added
   * @param supportedDimensionNames names added to a non-empty {@code supported_dimensions_mask}
   * @param requiredDimensionNames names added to a non-empty {@code required_dimensions_mask}
   */
  public record Widening(
      AddedSortKey labHostName,
      AddedSortKey deviceId,
      boolean deviceStatus,
      boolean deviceFeature,
      ImmutableSet<WidenedFeatureField> featureFields,
      Optional<DimensionNameMatcher> supportedDimensionNames,
      Optional<DimensionNameMatcher> requiredDimensionNames) {

    static Widening labOnly(AddedSortKey labHostName) {
      return new Widening(
          labHostName,
          AddedSortKey.NOT_ADDED,
          /* deviceStatus= */ false,
          /* deviceFeature= */ false,
          ImmutableSet.of(),
          Optional.empty(),
          Optional.empty());
    }

    /** Whether the push-down masks equal the client mask. */
    public boolean isEmpty() {
      return labHostName == AddedSortKey.NOT_ADDED && !touchesDevices();
    }

    boolean touchesDevices() {
      return deviceId != AddedSortKey.NOT_ADDED
          || deviceStatus
          || !featureFields.isEmpty()
          || supportedDimensionNames.isPresent()
          || requiredDimensionNames.isPresent();
    }
  }

  /** Plans the projection for {@code query}. */
  public static Projection of(LabQuery query) {
    Mask.Builder pushDownMask = query.getMask().toBuilder();
    AddedSortKey labHostName = widenLabInfoMask(pushDownMask, query);
    Widening widening = widenDeviceInfoMask(pushDownMask, query, labHostName);
    return new Projection(
        pushDownMask.hasLabInfoMask()
            ? CompiledLabInfoMask.of(pushDownMask.getLabInfoMask())
            : CompiledLabInfoMask.retainAll(),
        pushDownMask.hasDeviceInfoMask()
            ? CompiledDeviceInfoMask.of(pushDownMask.getDeviceInfoMask())
            : CompiledDeviceInfoMask.retainAll(),
        widening);
  }

  /** Labs are ordered by host name in the lab view only; the device view never orders labs. */
  private static AddedSortKey widenLabInfoMask(Mask.Builder pushDownMask, LabQuery query) {
    FieldMask clientFieldMask = query.getMask().getLabInfoMask().getFieldMask();
    if (query.hasDeviceViewRequest() || !projectsFields(clientFieldMask)) {
      return AddedSortKey.NOT_ADDED;
    }
    ImmutableSet<String> added =
        addMissingPaths(
            pushDownMask.getLabInfoMaskBuilder().getFieldMaskBuilder(),
            Stream.of(LAB_SORT_KEY_PATH));
    return sortKeyAdded(added.contains(LAB_SORT_KEY_PATH), clientFieldMask, LAB_LOCATOR_PATH);
  }

  private static AddedSortKey sortKeyAdded(
      boolean added, FieldMask clientFieldMask, String locatorPath) {
    if (!added) {
      return AddedSortKey.NOT_ADDED;
    }
    return MaskUtils.isFieldRequested(clientFieldMask, locatorPath)
        ? AddedSortKey.CLEAR_KEY
        : AddedSortKey.CLEAR_LOCATOR;
  }

  /** Devices are ordered by UUID in both views, and grouped by the query's group operations. */
  private static Widening widenDeviceInfoMask(
      Mask.Builder pushDownMask, LabQuery query, AddedSortKey labHostName) {
    DeviceInfoMask clientMask = query.getMask().getDeviceInfoMask();
    boolean dropsDeviceInfo =
        clientMask.hasFieldMask() && clientMask.getFieldMask().getPathsCount() == 0;
    if (!query.getMask().hasDeviceInfoMask() || dropsDeviceInfo) {
      return Widening.labOnly(labHostName);
    }
    ImmutableSet<DeviceGroupCondition> groupConditions =
        query.getDeviceViewRequest().getDeviceGroupOperationList().stream()
            .map(DeviceGroupOperation::getDeviceGroupCondition)
            .collect(toImmutableSet());
    DeviceInfoMask.Builder pushDownDeviceInfoMask = pushDownMask.getDeviceInfoMaskBuilder();

    ImmutableSet<String> addedPaths =
        projectsFields(clientMask.getFieldMask())
            ? addMissingPaths(
                pushDownDeviceInfoMask.getFieldMaskBuilder(),
                Stream.concat(
                    Stream.of(DEVICE_SORT_KEY_PATH),
                    groupConditions.stream().flatMap(condition -> fieldReadBy(condition).stream())))
            : ImmutableSet.of();
    ImmutableSet<WidenedFeatureField> featureFields =
        WidenedFeatureField.ALL.stream()
            .filter(field -> addedPaths.contains(field.path))
            .collect(toImmutableSet());

    ImmutableSet<String> groupByDimensionNames =
        groupConditions.stream()
            .flatMap(condition -> dimensionNameReadBy(condition).stream())
            .map(name -> toLowerCase(name))
            .collect(toImmutableSet());
    Optional<DimensionNameMatcher> supportedDimensionNames = Optional.empty();
    Optional<DimensionNameMatcher> requiredDimensionNames = Optional.empty();
    if (pushDownDeviceInfoMask.hasSupportedDimensionsMask()) {
      supportedDimensionNames =
          addMissingDimensionNames(
              pushDownDeviceInfoMask.getSupportedDimensionsMaskBuilder(), groupByDimensionNames);
    }
    if (pushDownDeviceInfoMask.hasRequiredDimensionsMask()) {
      requiredDimensionNames =
          addMissingDimensionNames(
              pushDownDeviceInfoMask.getRequiredDimensionsMaskBuilder(), groupByDimensionNames);
    }
    return new Widening(
        labHostName,
        sortKeyAdded(
            addedPaths.contains(DEVICE_SORT_KEY_PATH),
            clientMask.getFieldMask(),
            DEVICE_LOCATOR_PATH),
        addedPaths.contains(DEVICE_STATUS_PATH),
        !featureFields.isEmpty()
            && !MaskUtils.isFieldRequested(clientMask.getFieldMask(), DEVICE_FEATURE_PATH),
        featureFields,
        supportedDimensionNames,
        requiredDimensionNames);
  }

  /**
   * Whether {@code fieldMask} selects a subset of fields. An absent mask (no paths) keeps every
   * field and an explicitly empty mask (no paths either) drops the message; neither is widened.
   */
  private static boolean projectsFields(FieldMask fieldMask) {
    return fieldMask.getPathsCount() > 0;
  }

  /** Adds each path that {@code fieldMask} does not already cover and returns the added ones. */
  private static ImmutableSet<String> addMissingPaths(
      FieldMask.Builder fieldMask, Stream<String> paths) {
    FieldMask before = fieldMask.build();
    ImmutableSet<String> added =
        paths
            .distinct()
            .filter(path -> !MaskUtils.isFieldRequested(before, path))
            .collect(toImmutableSet());
    added.forEach(fieldMask::addPaths);
    return added;
  }

  /**
   * Adds each of {@code names} that a non-empty whitelist lacks, comparing case-insensitively, and
   * returns a matcher for the added names, or empty when nothing was added. An empty whitelist
   * already keeps every dimension and is left alone.
   */
  private static Optional<DimensionNameMatcher> addMissingDimensionNames(
      DimensionsMask.Builder whitelist, ImmutableSet<String> names) {
    if (whitelist.getDimensionNamesCount() == 0) {
      return Optional.empty();
    }
    ImmutableSet<String> existing =
        whitelist.getDimensionNamesList().stream()
            .map(name -> toLowerCase(name))
            .collect(toImmutableSet());
    ImmutableSet<String> added =
        names.stream().filter(name -> !existing.contains(name)).collect(toImmutableSet());
    added.forEach(whitelist::addDimensionNames);
    return added.isEmpty() ? Optional.empty() : Optional.of(DimensionNameMatcher.of(added));
  }

  /**
   * The {@code DeviceInfo} field a group-by condition reads, mirroring {@link
   * LabQueryUtils#doOneGroupOperation}. Exhaustive on purpose: a new condition kind must declare
   * what it reads here before it can compile.
   */
  private static Optional<String> fieldReadBy(DeviceGroupCondition condition) {
    return switch (condition.getConditionCase()) {
      case SINGLE_DIMENSION_VALUE, DIMENSION_VALUE_LIST ->
          Optional.of(WidenedFeatureField.COMPOSITE_DIMENSION.path);
      case TYPE_LIST -> Optional.of(WidenedFeatureField.TYPE.path);
      case OWNER_LIST -> Optional.of(WidenedFeatureField.OWNER.path);
      case EXECUTOR_LIST -> Optional.of(WidenedFeatureField.EXECUTOR.path);
      case SINGLE_STATUS -> Optional.of(DEVICE_STATUS_PATH);
      case CONDITION_NOT_SET -> Optional.empty();
    };
  }

  /** The dimension name a dimension group-by reads from both dimension lists, if any. */
  private static Optional<String> dimensionNameReadBy(DeviceGroupCondition condition) {
    return switch (condition.getConditionCase()) {
      case SINGLE_DIMENSION_VALUE ->
          Optional.of(condition.getSingleDimensionValue().getDimensionName());
      case DIMENSION_VALUE_LIST ->
          Optional.of(condition.getDimensionValueList().getDimensionName());
      default -> Optional.empty();
    };
  }

  /**
   * Removes from {@code result} everything the push-down masks kept beyond the client mask. Call
   * after ordering and grouping; a no-op when {@link Widening#isEmpty}.
   */
  public void undoWidening(LabQueryResult.Builder result) {
    if (widening.isEmpty()) {
      return;
    }
    if (result.hasLabView()) {
      for (LabData.Builder labData : result.getLabViewBuilder().getLabDataBuilderList()) {
        if (labData.hasLabInfo() && labData.getLabInfo().hasLabLocator()) {
          switch (widening.labHostName()) {
            case NOT_ADDED -> {}
            case CLEAR_KEY -> labData.getLabInfoBuilder().getLabLocatorBuilder().clearHostName();
            case CLEAR_LOCATOR -> labData.getLabInfoBuilder().clearLabLocator();
          }
        }
        if (widening.touchesDevices() && labData.hasDeviceList()) {
          undoDeviceList(labData.getDeviceListBuilder());
        }
      }
    }
    if (result.hasDeviceView() && widening.touchesDevices()) {
      undoGroupedDevices(result.getDeviceViewBuilder().getGroupedDevicesBuilder());
    }
  }

  private void undoGroupedDevices(GroupedDevices.Builder groupedDevices) {
    if (groupedDevices.hasDeviceList()) {
      undoDeviceList(groupedDevices.getDeviceListBuilder());
    } else if (groupedDevices.hasDeviceGroupResult()) {
      for (DeviceGroup.Builder group :
          groupedDevices.getDeviceGroupResultBuilder().getDeviceGroupBuilderList()) {
        if (group.hasGroupedDevices()) {
          undoGroupedDevices(group.getGroupedDevicesBuilder());
        }
      }
    }
  }

  private void undoDeviceList(DeviceList.Builder deviceList) {
    List<DeviceInfo> deviceInfos = deviceList.getDeviceInfoList();
    ImmutableList.Builder<DeviceInfo> undone =
        ImmutableList.builderWithExpectedSize(deviceInfos.size());
    for (DeviceInfo deviceInfo : deviceInfos) {
      undone.add(undoDeviceInfo(deviceInfo));
    }
    deviceList.clearDeviceInfo().addAllDeviceInfo(undone.build());
  }

  private DeviceInfo undoDeviceInfo(DeviceInfo deviceInfo) {
    DeviceInfo.Builder builder = deviceInfo.toBuilder();
    if (deviceInfo.hasDeviceLocator()) {
      switch (widening.deviceId()) {
        case NOT_ADDED -> {}
        case CLEAR_KEY -> builder.getDeviceLocatorBuilder().clearId();
        case CLEAR_LOCATOR -> builder.clearDeviceLocator();
      }
    }
    if (widening.deviceStatus()) {
      builder.clearDeviceStatus();
    }
    if (deviceInfo.hasDeviceFeature()) {
      if (widening.deviceFeature()) {
        builder.clearDeviceFeature();
      } else {
        undoDeviceFeature(builder.getDeviceFeatureBuilder());
      }
    }
    return builder.build();
  }

  private void undoDeviceFeature(DeviceFeature.Builder feature) {
    for (WidenedFeatureField field : widening.featureFields()) {
      field.clear(feature);
    }
    if (!feature.hasCompositeDimension()) {
      return;
    }
    DeviceCompositeDimension.Builder composite = feature.getCompositeDimensionBuilder();
    if (widening.supportedDimensionNames().isPresent()) {
      ImmutableList<DeviceDimension> kept =
          without(widening.supportedDimensionNames().get(), composite.getSupportedDimensionList());
      composite.clearSupportedDimension().addAllSupportedDimension(kept);
    }
    if (widening.requiredDimensionNames().isPresent()) {
      ImmutableList<DeviceDimension> kept =
          without(widening.requiredDimensionNames().get(), composite.getRequiredDimensionList());
      composite.clearRequiredDimension().addAllRequiredDimension(kept);
    }
  }

  private static ImmutableList<DeviceDimension> without(
      DimensionNameMatcher names, List<DeviceDimension> dimensions) {
    ImmutableList.Builder<DeviceDimension> kept = ImmutableList.builder();
    for (DeviceDimension dimension : dimensions) {
      if (!names.matches(dimension.getName())) {
        kept.add(dimension);
      }
    }
    return kept.build();
  }
}
