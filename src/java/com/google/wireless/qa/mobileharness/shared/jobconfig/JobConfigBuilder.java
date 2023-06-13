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

package com.google.wireless.qa.mobileharness.shared.jobconfig;

import com.google.api.client.util.Strings;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.job.JobTypeUtil;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.FileConfigList;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringList;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;

/**
 * Builder of {@link com.google.wireless.qa.mobileharness.shared.proto.JobConfig}, provides a set of
 * handy methods for it.
 */
public final class JobConfigBuilder {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The wrapped protobuf builder, which is used to generate the final protobuf. */
  private final JobConfig.Builder config;

  /**
   * Constructs a new {@link JobConfigBuilder} with {@code jobConfig} as the initial value.
   *
   * @param jobConfig the initial message of builder
   */
  private JobConfigBuilder(JobConfig jobConfig) {
    this.config = jobConfig.toBuilder();
  }

  /**
   * Creates {@link JobConfigBuilder} with the default {@link JobConfig} instance as the initial
   * value. It is a shortcut of {@code fromProto(JobConfig.getDefaultInstance())}.
   */
  public static JobConfigBuilder fromDefaultProto() {
    return fromProto(JobConfig.getDefaultInstance());
  }

  /**
   * Creates {@link JobConfigBuilder} with {@code jobConfig} as the initial value.
   *
   * @param jobConfig the initial message of builder
   */
  public static JobConfigBuilder fromProto(JobConfig jobConfig) {
    return new JobConfigBuilder(jobConfig);
  }

  /**
   * Creates {@link JobConfigBuilder} from content of {@code path}.
   *
   * @param path file that contains proto text of {@link JobConfig}
   * @throws MobileHarnessException if content of {@code path} is not a valid proto text
   */
  public static JobConfigBuilder fromFile(String path) throws MobileHarnessException {
    LocalFileUtil fileUtil = new LocalFileUtil();
    JobConfigBuilder jobConfigBuilder = fromDefaultProto();
    try {
      TextFormat.merge(fileUtil.readFile(path), jobConfigBuilder.getProtoBuilder());
    } catch (ParseException | MobileHarnessException e) {
      throw new MobileHarnessException(
          ErrorCode.JOB_CONFIG_ERROR, "Failed to parse JobConfig proto text from file: " + path, e);
    }
    return jobConfigBuilder;
  }

  /**
   * Creates {@link JobConfigBuilder} with json string {@code json}.
   *
   * <p>Note that json string input in the old format:
   *
   * <p>"device": "AndroidRealDevice", "dimensions": {"label", "foo"},
   *
   * <p>will first be converted to the new json format that accounts for subdevices:
   *
   * <p>"device": [ ["AndroidRealDevice", {"label", "foo"}] ],
   *
   * <p>If the old and new styles are mixed (i.e., dimensions field is non-empty and device is
   * already an array) we throw a MobileHarnessException.
   *
   * @param json a json string of the job config
   */
  public static JobConfigBuilder fromJson(String json) throws MobileHarnessException {
    try {
      JsonObject configJson = JsonParser.parseString(json).getAsJsonObject();
      JsonElement typeObject = configJson.get("type");
      boolean typeSpecified = typeObject != null && !typeObject.isJsonNull();
      JsonElement deviceObject = configJson.get("device");
      // Dimensions, decorators, and scoped specs are all moved into deviceObject, only if the
      // deviceObject is a String (and hence not an adhoc testbed definition).
      JsonElement dimensionsElement = configJson.remove("dimensions");
      JsonObject dimensionsObject =
          dimensionsElement == null || dimensionsElement.isJsonNull()
              ? new JsonObject()
              : dimensionsElement.getAsJsonObject();
      JsonElement driverObject = configJson.get("driver");
      boolean driverSpecified = driverObject != null && !driverObject.isJsonNull();
      JsonElement decoratorsElement = configJson.remove("decorators");
      JsonArray decoratorsArray =
          decoratorsElement == null || decoratorsElement.isJsonNull()
              ? new JsonArray()
              : decoratorsElement.getAsJsonArray();
      boolean subDeviceSpecsSpecified = false;
      /**
       * If the deprecated type argument is used then the device field of the job config json will
       * be missing or a json null. First address the case for old style device & dimensions args
       * usage. In this case 1) The dimensions field should be removed. 2) The decorators field
       * should be removed. 3) If the device arg is a string, convert the string and previously
       * removed dimensions map and decorators list to a new-style SubDeviceSpec tuple 3) If the
       * device arg is not a string (e.g., if it is already a JsonArray of 2-entry JsonArrays) then
       * an exception should be thrown because we are mixing specification styles (old vs. new)
       */
      if (deviceObject != null && !deviceObject.isJsonNull()) {
        subDeviceSpecsSpecified =
            !deviceObject.isJsonPrimitive() || !deviceObject.getAsJsonPrimitive().isString();
        if (!subDeviceSpecsSpecified) {
          configJson.add(
              "device",
              createDeviceList(deviceObject.getAsString(), dimensionsObject, decoratorsArray));
          json = configJson.toString();
        } else if (hasDimensions(dimensionsObject)) {
          throw new MobileHarnessException(
              ErrorCode.JOB_CONFIG_ERROR,
              "JobConfig mixing dimension specification syntax is not allowed:" + json);
        } else if (hasDecorators(decoratorsArray)) {
          throw new MobileHarnessException(
              ErrorCode.JOB_CONFIG_ERROR,
              "JobConfig mixing decorator specification syntaxes is not allowed:" + json);
        }
      } else if (typeSpecified) {
        /**
         * If device is null/missing, address the type & dimensions args usage in the following way:
         * 1) The dimensions field should be removed. 2) The decorators field should be removed. 3)
         * Determine the device type based on the type string. 4) Create a Decorators list based on
         * the type string. 4) Convert the string, previously removed dimensions map, and decorators
         * list to a new-style SubDeviceSpec tuple.
         */
        JobType jobType = JobTypeUtil.parseString(typeObject.getAsString());
        String deviceType = jobType.getDevice();
        // Reverse the type decorator list order to convert it to to a subdevice decorator list.
        // This is necessary because `type="D4+D3+D2+D1"` is equivalent to
        // `decorators = [D1, D2, D3, D4]`.
        // the invocation order of both way is:
        // MH {
        //   D1 {
        //     D2 {
        //       D3 {
        //          D4;
        //       }
        //    }
        // }
        ImmutableList<String> decorators =
            ImmutableList.copyOf(jobType.getDecoratorList()).reverse();
        for (String decorator : decorators) {
          decoratorsArray.add(decorator);
        }
        configJson.add("device", createDeviceList(deviceType, dimensionsObject, decoratorsArray));
        json = configJson.toString();
      } else if (deviceObject == null && driverSpecified) {
        if (hasDimensions(dimensionsObject)) {
          throw new MobileHarnessException(
              ErrorCode.JOB_CONFIG_ERROR,
              "JobConfig mixing dimension specification and target_device syntax is not allowed:"
                  + json);
        }
        configJson.add("device", createDeviceList("", new JsonObject(), decoratorsArray));
        json = configJson.toString();
      }
      logger.atInfo().log("The json config was re-written as %s", json);

      return new JobConfigBuilder(JobConfigGsonHolder.getGson().fromJson(json, JobConfig.class))
          .updateParamStats(typeSpecified, subDeviceSpecsSpecified);
    } catch (JsonParseException | IllegalStateException e) {
      throw new MobileHarnessException(
          ErrorCode.JOB_CONFIG_ERROR,
          "Failed to parse JobConfig from json:" + JobConfigGsonHolder.prettyJson(json),
          e);
    }
  }

  /** Returns whether {@code decoratorsArray} has at least one decorator. */
  private static boolean hasDecorators(JsonArray decoratorsArray) {
    return !decoratorsArray.isJsonNull() && decoratorsArray.size() > 0;
  }

  /** Returns whether {@code dimensionsObject} has at least one dimension specified. */
  private static boolean hasDimensions(JsonObject dimensionsObject) {
    return !dimensionsObject.isJsonNull() && !dimensionsObject.entrySet().isEmpty();
  }

  /**
   * Updates the param_stats of a {@link JobConfig} by the given flags if the old type style or the
   * new sub_device_specs style are used.
   */
  @CanIgnoreReturnValue
  private JobConfigBuilder updateParamStats(
      boolean typeSpecified, boolean subDeviceSpecsSpecified) {
    JobConfig.Builder configBuilder = getProtoBuilder();
    StringMap.Builder paramStatsBuilder = configBuilder.getParamStats().toBuilder();
    if (typeSpecified) {
      paramStatsBuilder.putContent("type", "");
    }
    if (subDeviceSpecsSpecified) {
      paramStatsBuilder.putContent("sub_device_specs", "");
    }
    if (configBuilder.getSharedDimensionNames().getContentCount() > 0) {
      paramStatsBuilder.putContent("shared_dimension_names", "");
    }
    if (configBuilder.getSpecFiles().getContentCount() > 0) {
      paramStatsBuilder.putContent("spec_files", "");
    }
    if (paramStatsBuilder.getContentCount() > 0) {
      configBuilder.setParamStats(paramStatsBuilder.build());
    }
    return this;
  }

  private static JsonArray createDeviceList(
      String deviceType, JsonObject dimensions, JsonArray decorators) {
    JsonArray deviceList = new JsonArray();
    JsonArray combinedDeviceSpec = new JsonArray();
    combinedDeviceSpec.add(deviceType);
    combinedDeviceSpec.add(dimensions);
    combinedDeviceSpec.add(decorators);
    deviceList.add(combinedDeviceSpec);
    return deviceList;
  }

  /** Gets wrapped protobuf builder. */
  public JobConfig.Builder getProtoBuilder() {
    return config;
  }

  /** Clears contents of protobuf. */
  public void clear() {
    config.clear();
  }

  /** Builds {@link JobConfig}. */
  public JobConfig build() {
    return config.build();
  }

  @Override
  public String toString() {
    try {
      // Use json first, which is more compact.
      return toJson();
    } catch (MobileHarnessException e) {
      return config.toString();
    }
  }

  /**
   * Sets the {@code field} in {@code config} with {@value}. It converts {@code value} to from
   * {@link Map} to {@link StringMap} or {@link FileConfigList}, or converts it from {@link List} to
   * {@link StringList} first if necessary.
   */
  @CanIgnoreReturnValue
  public JobConfigBuilder setField(FieldDescriptor field, Object value)
      throws MobileHarnessException {
    config.setField(field, JobConfigBuilder.getConvertedValue(field, value));
    return this;
  }

  @SuppressWarnings("unchecked")
  public static Object getConvertedValue(FieldDescriptor field, Object originalValue)
      throws MobileHarnessException {
    Object value = originalValue;
    JavaType javaType = field.getJavaType();
    if (javaType == JavaType.MESSAGE) {
      Descriptor messageType = field.getMessageType();
      // Cast a java type Map, List to the required message type in JobConfigBuilder. It's
      // impossible to get the generic parameter type in runtime (Java compiler wiped them), so we
      // just cast them to the proper generic type blindly and catch ClassCastException.
      // Actually it shouldn't fail to cast because this private method is only called in method
      // 'build', which guarantees the validation of inputs. If an exception throws, we need to
      // recheck the definition of JobConfigBuilder, there must be some incompatible fields added to
      // it.
      try {
        if (messageType == StringMap.getDescriptor() && value instanceof Map) {
          value = StringMap.newBuilder().putAllContent((Map<String, String>) originalValue).build();
        } else if (messageType == StringList.getDescriptor() && value instanceof List) {
          value = StringList.newBuilder().addAllContent((List<String>) originalValue).build();
        } else if (messageType == FileConfigList.getDescriptor() && value instanceof Map) {
          value = convertToFileConfigList((Map<String, List<String>>) originalValue);
        }
      } catch (ClassCastException e) {
        throw new MobileHarnessException(
            ErrorCode.JOB_CONFIG_ERROR,
            String.format(
                "Failed to cast value %s to message type %s.", value, messageType.getName()),
            e);
      }
    } else if (javaType == JavaType.ENUM && value instanceof ProtocolMessageEnum) {
      value = ((ProtocolMessageEnum) originalValue).getValueDescriptor();
    }
    return value;
  }

  /** Gets builder of {@code field}. */
  public Message.Builder getFieldBuilder(FieldDescriptor field) {
    return config.getFieldBuilder(field);
  }

  /** Gets value of {@code field}. */
  public Object getField(FieldDescriptor field) {
    return config.getField(field);
  }

  /** Clear value of {@code field}. */
  @CanIgnoreReturnValue
  public JobConfigBuilder clearField(FieldDescriptor field) {
    config.clearField(field);
    return this;
  }

  /** Validates content of protobuf. Throws MobileHarnessException if it is invalid. */
  public void validate() throws MobileHarnessException {
    validateDevices();
    validateFiles();
  }

  @VisibleForTesting
  void validateDevices() throws MobileHarnessException {
    if (!config.hasDevice() || config.getDevice().getSubDeviceSpecList().isEmpty()) {
      // The !config.hasDevice() case should have been caught in validate(), but check again.
      throw new MobileHarnessException(
          ErrorCode.JOB_CONFIG_ERROR, "Device-subdevices are not initialized.");
    }
  }

  @VisibleForTesting
  void validateFiles() {
    // All files must not be null or empty.
    JobConfig.FileConfigList.Builder files = config.getFilesBuilder();
    for (int i = config.getFiles().getContentCount() - 1; i >= 0; i--) {
      JobConfig.FileConfigList.FileConfig.Builder content = files.getContentBuilder(i);
      if (Strings.isNullOrEmpty(content.getTag())) {
        files.removeContent(i);
        continue;
      }
      List<String> nonemptyPaths = new ArrayList<>();
      for (String path : content.getPathList()) {
        if (Strings.isNullOrEmpty(path)) {
          logger.atWarning().log(
              "%s",
              StrUtil.addFrame(
                  "Ignore empty file path of tag : " + content.getTag() + ". Please fix it ASAP."));
        } else {
          nonemptyPaths.add(path);
        }
      }
      if (nonemptyPaths.isEmpty()) {
        files.removeContent(i);
      } else {
        content.clearPath();
        content.addAllPath(nonemptyPaths);
      }
    }
  }

  /**
   * Merges {@link JobConfigBuilder} {@code other}. If a field is set both in {@code this} and
   * {@code other}, ignore the one in {@code other}. Does nothing if {@code other} is null.
   */
  @CanIgnoreReturnValue
  public JobConfigBuilder merge(@Nullable JobConfigBuilder other) {
    if (other == null) {
      return this;
    }
    for (FieldDescriptor field : JobConfig.getDescriptor().getFields()) {
      if (!config.hasField(field) && other.config.hasField(field)) {
        config.setField(field, other.config.getField(field));
      }
    }
    return this;
  }

  /** Converts {@code map} to a {@link FileConfigList}. */
  private static FileConfigList convertToFileConfigList(@Nullable Map<String, List<String>> map) {
    FileConfigList.Builder builder = FileConfigList.newBuilder();
    for (Entry<String, List<String>> entry : map.entrySet()) {
      builder.addContentBuilder().setTag(entry.getKey()).addAllPath(entry.getValue());
    }
    return builder.build();
  }

  /** Serializes JobConfig to a json string. */
  public String toJson() throws MobileHarnessException {
    try {
      return JobConfigGsonHolder.getGson().toJson(config);
    } catch (JsonParseException e) {
      throw new MobileHarnessException(
          ErrorCode.JOB_CONFIG_ERROR,
          "Failed to serialize proto to json. proto message: " + config,
          e);
    }
  }
}
