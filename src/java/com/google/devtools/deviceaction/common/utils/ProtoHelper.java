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

import static com.google.devtools.deviceaction.common.utils.Constants.GCS_PREFIX;
import static com.google.devtools.deviceaction.common.utils.Constants.SEPARATOR;
import static com.google.devtools.deviceaction.common.utils.Constants.SERIAL_KEY;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.ActionOptions;
import com.google.devtools.deviceaction.common.schemas.ActionOptions.Options;
import com.google.devtools.deviceaction.common.schemas.Command;
import com.google.devtools.deviceaction.framework.proto.ActionSpec;
import com.google.devtools.deviceaction.framework.proto.DeviceConfig;
import com.google.devtools.deviceaction.framework.proto.DeviceType;
import com.google.devtools.deviceaction.framework.proto.FileSpec;
import com.google.devtools.deviceaction.framework.proto.GCSFile;
import com.google.devtools.deviceaction.framework.proto.Operand;
import com.google.devtools.deviceaction.framework.proto.Unary;
import com.google.devtools.deviceaction.framework.proto.action.InstallMainlineSpec;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;

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

  /** Gets the first device if possible. */
  public static Optional<Operand> getFirst(ActionSpec actionSpec) {
    if (actionSpec.hasUnary()) {
      return Optional.of(actionSpec.getUnary().getFirst());
    } else if (actionSpec.hasBinary()) {
      return Optional.of(actionSpec.getBinary().getFirst());
    }
    return Optional.empty();
  }

  /** Gets the second device if possible. */
  public static Optional<Operand> getSecond(ActionSpec actionSpec) {
    if (actionSpec.hasBinary()) {
      return Optional.of(actionSpec.getBinary().getSecond());
    }
    return Optional.empty();
  }

  /** Gets {@code DeviceConfig} by parsing a text proto. */
  public static DeviceConfig getDeviceConfigFromTextproto(String protoAsString, Command cmd)
      throws DeviceActionException {
    ExtensionRegistry registry = getExtensionRegistry(cmd);
    try {
      return TextFormat.parse(protoAsString, registry, DeviceConfig.class);
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
            "Need to set the first device in " + options);
        InstallMainlineSpec installMainlineSpec =
            buildProtoByOptions(options.action(), InstallMainlineSpec.class);
        return ActionSpec.newBuilder()
            .setUnary(
                Unary.newBuilder()
                    .setFirst(
                        Operand.newBuilder()
                            .setDeviceType(DeviceType.ANDROID_PHONE)
                            .setUuid(getUuid(options.firstDevice()))
                            .build())
                    .setExtension(InstallMainlineSpec.ext, installMainlineSpec)
                    .build())
            .build();
      default:
        throw new DeviceActionException(INVALID_CMD, ErrorType.CUSTOMER_ISSUE, "Not supported");
    }
  }

  /**
   * Gets {@code ActionSpec} from {@code DeviceConfig}.
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
      default:
        throw new DeviceActionException(INVALID_CMD, ErrorType.CUSTOMER_ISSUE, "Not supported");
    }
  }

  @VisibleForTesting
  @SuppressWarnings("unchecked")
  static <T extends Message> T buildProtoByOptions(Options options, Class<T> clazz)
      throws DeviceActionException {
    T defaultInstance;
    try {
      // T.getDefaultInstance() always returns T.
      defaultInstance = (T) clazz.getMethod("getDefaultInstance").invoke(clazz);
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
            builder.addRepeatedField(fieldDescriptor, options.keyValues().get(name));
          } else {
            options.getOnlyValue(name).ifPresent(v -> builder.setField(fieldDescriptor, v));
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
      default:
        throw new DeviceActionException(
            "UNSUPPORTED_CMD",
            ErrorType.CUSTOMER_ISSUE,
            String.format("The cmd %s is not supported.", cmd));
    }
    return registry;
  }

  private static FileSpec parseFileSpec(String tag, String value) {
    boolean isGCS = value.startsWith("gcs:");
    if (isGCS) {
      List<String> split = Splitter.on(SEPARATOR).splitToList(value.substring(GCS_PREFIX.length()));
      return FileSpec.newBuilder()
          .setTag(tag)
          .setGcsFile(GCSFile.newBuilder().setProject(split.get(0)).setGsUri(split.get(1)).build())
          .build();
    }
    return FileSpec.newBuilder().setTag(tag).setLocalPath(value).build();
  }

  private ProtoHelper() {}
}
