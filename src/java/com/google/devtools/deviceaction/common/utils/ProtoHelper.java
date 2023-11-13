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

package com.google.devtools.deviceaction.common.utils;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.deviceaction.common.utils.Constants.DEVICE_CONFIG_KEY;
import static com.google.devtools.deviceaction.common.utils.Constants.GCS_PREFIX;
import static com.google.devtools.deviceaction.common.utils.Constants.PROPERTY_SEPARATOR;
import static com.google.devtools.deviceaction.common.utils.Constants.SERIAL_KEY;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.ActionOptions;
import com.google.devtools.deviceaction.common.schemas.ActionOptions.Options;
import com.google.devtools.deviceaction.common.schemas.Command;
import com.google.devtools.deviceaction.common.schemas.DevicePosition;
import com.google.devtools.deviceaction.common.schemas.DeviceWrapper;
import com.google.devtools.deviceaction.framework.proto.ActionSpec;
import com.google.devtools.deviceaction.framework.proto.DeviceConfig;
import com.google.devtools.deviceaction.framework.proto.DeviceType;
import com.google.devtools.deviceaction.framework.proto.FileSpec;
import com.google.devtools.deviceaction.framework.proto.GCSFile;
import com.google.devtools.deviceaction.framework.proto.Operand;
import com.google.devtools.deviceaction.framework.proto.Unary;
import com.google.devtools.deviceaction.framework.proto.action.InstallMainlineSpec;
import com.google.devtools.deviceaction.framework.proto.action.ResetSpec;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import javax.annotation.Nullable;

/** A helper class to handle protos. */
public class ProtoHelper {

  private static final String REFLECTION_ERROR = "REFLECTION_ERROR";
  private static final String INVALID_CMD = "INVALID_CMD";

  /** Merges the specs from device config and the action. */
  public static ActionSpec mergeActionSpec(
      Command cmd, ActionSpec actionSpec, DeviceConfig deviceConfig) throws DeviceActionException {
    ActionSpec.Builder builder = ActionSpec.newBuilder();
    builder.mergeFrom(getActionSpec(cmd, deviceConfig));
    builder.mergeFrom(actionSpec);
    return builder.build();
  }

  /**
   * Gets a map associating {@link DeviceWrapper} to {@link DevicePosition} by parsing {@link
   * ActionSpec}.
   *
   * @param actionSpec to extract the operand info.
   * @return a map to get {@link DeviceWrapper} for each available position.
   * @throws DeviceActionException if the input is invalid.
   */
  public static EnumMap<DevicePosition, DeviceWrapper> getDeviceWrapperMap(ActionSpec actionSpec)
      throws DeviceActionException {
    return getDeviceWrapperMapImp(actionSpec, null);
  }

  /**
   * Gets a map associating {@link DeviceWrapper} to {@link DevicePosition} by parsing {@link
   * ActionSpec} and {@link ActionOptions}.
   *
   * @param actionSpec to extract the operand info.
   * @param options to extract the possible device config file.
   * @return a map to get {@link DeviceWrapper} for each available position.
   * @throws DeviceActionException if the inputs are invalid.
   */
  public static EnumMap<DevicePosition, DeviceWrapper> getDeviceWrapperMap(
      ActionSpec actionSpec, ActionOptions options) throws DeviceActionException {
    Conditions.checkArgument(
        options != null, ErrorType.CUSTOMER_ISSUE, "Options should not be null!");
    return getDeviceWrapperMapImp(actionSpec, options);
  }

  /**
   * Gets {@code DeviceConfig} by parsing a text proto.
   *
   * <p>The result only includes the extensions corresponding to the {@code cmd}. Irrelevant or
   * unknown extensions will not be included.
   */
  public static DeviceConfig getDeviceConfigFromTextproto(String protoAsString, Command cmd)
      throws DeviceActionException {
    ExtensionRegistry registry = getExtensionRegistry(cmd);
    TextFormat.Parser parser =
        TextFormat.Parser.newBuilder().setAllowUnknownExtensions(true).build();
    try {
      DeviceConfig.Builder builder = DeviceConfig.newBuilder();
      parser.merge(protoAsString, registry, builder);
      return builder.build();
    } catch (ParseException e) {
      throw new DeviceActionException(
          "PROTO_ERROR",
          ErrorType.DEPENDENCY_ISSUE,
          "Failed to parse textproto file\n" + protoAsString,
          e);
    }
  }

  /**
   * Gets {@code ActionSpec} by parsing options.
   *
   * @throws DeviceActionException if the command is not supported.
   */
  public static ActionSpec getActionSpec(ActionOptions options) throws DeviceActionException {
    switch (options.command()) {
      case INSTALL_MAINLINE:
        Conditions.checkArgument(
            options.firstDevice() != null,
            ErrorType.CUSTOMER_ISSUE,
            "Need to set the first device in %s",
            options);
        InstallMainlineSpec installMainlineSpec =
            buildProtoByOptions(options.action(), InstallMainlineSpec.class);
        return getActionSpecForInstallMainline(installMainlineSpec, getUuid(options.firstDevice()));
      case RESET:
        Conditions.checkArgument(
            options.firstDevice() != null,
            ErrorType.CUSTOMER_ISSUE,
            "Need to set the first device in %s",
            options);
        ResetSpec resetSpec = buildProtoByOptions(options.action(), ResetSpec.class);
        return getActionSpecForReset(resetSpec, getUuid(options.firstDevice()));
      default:
        throw new DeviceActionException(INVALID_CMD, ErrorType.CUSTOMER_ISSUE, "Not supported");
    }
  }

  /**
   * Gets {@link ActionSpec} for {@code cmd} from {@link DeviceConfig}.
   *
   * <p>The method gets the extension corresponding to the {@code cmd} from {@link DeviceConfig} and
   * adds it to {@link ActionSpec}.
   *
   * @throws DeviceActionException if the command is not supported.
   */
  private static ActionSpec getActionSpec(Command cmd, DeviceConfig deviceConfig)
      throws DeviceActionException {
    switch (cmd) {
      case INSTALL_MAINLINE:
        InstallMainlineSpec installMainlineSpec =
            deviceConfig.getExtension(InstallMainlineSpec.installMainlineSpec);
        return ActionSpec.newBuilder()
            .setUnary(
                Unary.newBuilder()
                    .setExtension(InstallMainlineSpec.ext, installMainlineSpec)
                    .build())
            .build();
      case RESET:
        ResetSpec resetSpec = deviceConfig.getExtension(ResetSpec.resetSpec);
        return ActionSpec.newBuilder()
            .setUnary(Unary.newBuilder().setExtension(ResetSpec.ext, resetSpec).build())
            .build();
      default:
        throw new DeviceActionException(INVALID_CMD, ErrorType.CUSTOMER_ISSUE, "Not supported");
    }
  }

  /**
   * Gets {@link ActionSpec} for install mainline action.
   *
   * @param installMainlineSpec the spec of install mainline action.
   * @param uuid of the device.
   */
  public static ActionSpec getActionSpecForInstallMainline(
      InstallMainlineSpec installMainlineSpec, String uuid) {
    return ActionSpec.newBuilder()
        .setUnary(
            Unary.newBuilder()
                .setFirst(
                    Operand.newBuilder()
                        .setDeviceType(DeviceType.ANDROID_PHONE)
                        .setUuid(uuid)
                        .build())
                .setExtension(InstallMainlineSpec.ext, installMainlineSpec))
        .build();
  }

  /**
   * Gets {@link ActionSpec} for reset action.
   *
   * @param resetSpec the spec of reset action.
   * @param uuid of the device.
   */
  public static ActionSpec getActionSpecForReset(ResetSpec resetSpec, String uuid) {
    return ActionSpec.newBuilder()
        .setUnary(
            Unary.newBuilder()
                .setFirst(
                    Operand.newBuilder()
                        .setDeviceType(DeviceType.ANDROID_PHONE)
                        .setUuid(uuid)
                        .build())
                .setExtension(ResetSpec.ext, resetSpec))
        .build();
  }

  @VisibleForTesting
  @SuppressWarnings("unchecked")
  static <T extends Message> T buildProtoByOptions(Options options, Class<T> clazz)
      throws DeviceActionException {
    T defaultInstance;
    try {
      defaultInstance = clazz.cast(clazz.getMethod("getDefaultInstance").invoke(null));
    } catch (InvocationTargetException e) {
      throw new DeviceActionException(
          REFLECTION_ERROR, ErrorType.CUSTOMER_ISSUE, "Invocation target", e);
    } catch (IllegalAccessException e) {
      throw new DeviceActionException(
          REFLECTION_ERROR, ErrorType.CUSTOMER_ISSUE, "Access issue", e);
    } catch (NoSuchMethodException e) {
      throw new DeviceActionException(
          REFLECTION_ERROR, ErrorType.CUSTOMER_ISSUE, "Should never happen", e);
    }
    Message.Builder builder = defaultInstance.newBuilderForType();
    Descriptor descriptor = defaultInstance.getDescriptorForType();
    List<FieldDescriptor> fields = descriptor.getFields();
    for (FieldDescriptor fieldDescriptor : fields) {
      String name = fieldDescriptor.getName();
      switch (fieldDescriptor.getType()) {
        case BOOL:
          if (!fieldDescriptor.isRepeated()) {
            if (options.trueBoolOptions().contains(name)) {
              builder.setField(fieldDescriptor, /* value= */ true);
            } else if (options.falseBoolOptions().contains(name)) {
              builder.setField(fieldDescriptor, /* value= */ false);
            }
          }
          break;
        case STRING:
          if (fieldDescriptor.isRepeated()) {
            for (String val : options.keyValues().get(name)) {
              builder.addRepeatedField(fieldDescriptor, val);
            }
          } else {
            options.getOnlyValue(name).ifPresent(v -> builder.setField(fieldDescriptor, v));
          }
          break;
        case ENUM:
          EnumDescriptor enumDescriptor = fieldDescriptor.getEnumType();
          if (fieldDescriptor.isRepeated()) {
            for (EnumValueDescriptor valueDescriptor :
                options.keyValues().get(name).stream()
                    .map(enumDescriptor::findValueByName)
                    .collect(toImmutableList())) {
              builder.addRepeatedField(fieldDescriptor, valueDescriptor);
            }
          } else {
            options
                .getOnlyValue(name)
                .ifPresent(
                    v -> builder.setField(fieldDescriptor, enumDescriptor.findValueByName(v)));
          }
          break;
        case MESSAGE:
          if (fieldDescriptor.getMessageType().getName().equals("FileSpec")) {
            if (fieldDescriptor.isRepeated()) {
              for (Entry<String, String> entry : options.fileOptions().entries()) {
                builder.addRepeatedField(
                    fieldDescriptor, parseFileSpec(entry.getKey(), entry.getValue()));
              }
            }
          } else if (fieldDescriptor.getMessageType().getName().equals("Duration")) {
            if (!fieldDescriptor.isRepeated()) {
              options
                  .getOnlyValue(name)
                  .ifPresent(
                      v ->
                          builder.setField(
                              fieldDescriptor, TimeUtils.toProtoDuration(Duration.parse(v))));
            }
          }
          break;
        default:
          break;
      }
    }
    // Safe by contract of newBuilderForType().
    return (T) builder.build();
  }

  private static String getUuid(Options options) throws DeviceActionException {
    return options
        .getOnlyValue(SERIAL_KEY)
        .orElseThrow(
            () ->
                new DeviceActionException(
                    "ID_NOT_FOUND", ErrorType.CUSTOMER_ISSUE, "Missing option for serial."));
  }

  private static ExtensionRegistry getExtensionRegistry(Command cmd) throws DeviceActionException {
    ExtensionRegistry registry = ExtensionRegistry.newInstance();
    switch (cmd) {
      case INSTALL_MAINLINE:
        registry.add(InstallMainlineSpec.installMainlineSpec);
        break;
      case RESET:
        registry.add(ResetSpec.resetSpec);
        break;
      default:
        throw new DeviceActionException(
            INVALID_CMD,
            ErrorType.CUSTOMER_ISSUE,
            String.format("The cmd %s is not supported.", cmd));
    }
    return registry;
  }

  private static FileSpec parseFileSpec(String tag, String value) {
    boolean isGcs = value.startsWith(GCS_PREFIX);
    if (isGcs) {
      List<String> split =
          Splitter.on(PROPERTY_SEPARATOR).splitToList(value.substring(GCS_PREFIX.length()));
      return FileSpec.newBuilder()
          .setTag(tag)
          .setGcsFile(GCSFile.newBuilder().setProject(split.get(0)).setGsUri(split.get(1)).build())
          .build();
    }
    return FileSpec.newBuilder().setTag(tag).setLocalPath(value).build();
  }

  private static EnumMap<DevicePosition, DeviceWrapper> getDeviceWrapperMapImp(
      ActionSpec actionSpec, @Nullable ActionOptions options) throws DeviceActionException {
    EnumMap<DevicePosition, DeviceWrapper> map = new EnumMap<>(DevicePosition.class);
    if (actionSpec.hasUnary()) {
      Optional<String> firstOp =
          (options != null)
              ? options.firstDevice().getOnlyValue(DEVICE_CONFIG_KEY)
              : Optional.empty();
      DeviceWrapper firstWrapper = DeviceWrapper.create(actionSpec.getUnary().getFirst(), firstOp);
      map.put(DevicePosition.FIRST, firstWrapper);
    } else if (actionSpec.hasBinary()) {
      Optional<String> firstOp =
          (options != null)
              ? options.firstDevice().getOnlyValue(DEVICE_CONFIG_KEY)
              : Optional.empty();
      map.put(
          DevicePosition.FIRST, DeviceWrapper.create(actionSpec.getBinary().getFirst(), firstOp));
      Optional<String> secondOp =
          (options != null)
              ? options.secondDevice().getOnlyValue(DEVICE_CONFIG_KEY)
              : Optional.empty();
      map.put(
          DevicePosition.SECOND,
          DeviceWrapper.create(actionSpec.getBinary().getSecond(), secondOp));
    }
    return map;
  }

  private ProtoHelper() {}
}
