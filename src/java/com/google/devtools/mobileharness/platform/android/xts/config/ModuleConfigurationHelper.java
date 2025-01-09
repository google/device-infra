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

package com.google.devtools.mobileharness.platform.android.xts.config;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.api.model.job.in.Dimensions;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Device;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Option;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.TargetPreparer;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Test;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.DeviceConfigurationProto.DeviceGroup;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.DeviceConfigurationProto.ModuleDeviceConfiguration;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.ScopedSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.DriverDecoratorSpecMapper;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecHelper;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecWalker;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecWalker.Visitor;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import javax.annotation.Nullable;

/** A Helper class for xTS module configuration. */
public class ModuleConfigurationHelper {
  private static final String FILE_KEY = "file";
  private static final String DEVICE_DIMENSION_OPTION_NAME = "dimension";

  private static final ImmutableMap<String, String> DRIVER_ALIAS_MAP =
      ImmutableMap.of("MoblyAospPackageTest", "MoblyAospTest");
  private static final String ANY_DEVICE_ID = "*";

  private final ConfigurationUtil configurationUtil;

  public ModuleConfigurationHelper() {
    this.configurationUtil = new ConfigurationUtil();
  }

  /**
   * Updates JobInfo based on the given module configuration.
   *
   * @param jobInfo the {@link JobInfo} to update. The type of driver and device need to be correct
   * @param moduleConfig the {@link Configuration} for the module
   * @param moduleDeviceConfig unvalidated device config for the module if any
   * @param dependencies the list of {@link File} for files and directories that contain dependency
   *     files. Search files in directories in order
   */
  public void updateJobInfo(
      JobInfo jobInfo,
      Configuration moduleConfig,
      @Nullable ModuleDeviceConfiguration moduleDeviceConfig,
      List<File> dependencies)
      throws MobileHarnessException, InterruptedException {
    for (Option option : moduleConfig.getOptionsList()) {
      if (option.getKey().equals(FILE_KEY)) {
        jobInfo.files().add(option.getName(), resolveFilePath(option.getValue(), dependencies));
      } else {
        jobInfo.params().add(option.getName(), option.getValue());
      }
    }

    Visitor fileResolver = getFileResolver(dependencies);
    updateDriverSpecs(moduleConfig.getTest(), jobInfo, fileResolver);
    updateDeviceSpecs(
        moduleConfig.getDevicesList(), moduleDeviceConfig, jobInfo.subDeviceSpecs(), fileResolver);
  }

  private Visitor getFileResolver(List<File> dependencies) {
    return new JobSpecWalker.Visitor() {
      @Override
      public void visitPrimitiveFileField(Message.Builder builder, FieldDescriptor field)
          throws MobileHarnessException {
        if (field.getType() != FieldDescriptor.Type.STRING) {
          return;
        }

        if (field.isRepeated()) {
          for (int i = 0; i < builder.getRepeatedFieldCount(field); i++) {
            builder.setRepeatedField(
                field,
                i,
                resolveFilePath((String) builder.getRepeatedField(field, i), dependencies));
          }
        } else {
          builder.setField(field, resolveFilePath((String) builder.getField(field), dependencies));
        }
      }
    };
  }

  private void updateDriverSpecs(Test config, JobInfo jobInfo, Visitor fileResolver)
      throws MobileHarnessException, InterruptedException {
    String driverName = ConfigurationUtil.getSimpleClassName(config.getClazz());
    if (!jobInfo.type().getDriver().equals(driverName)
        && !jobInfo.type().getDriver().equals(DRIVER_ALIAS_MAP.get(driverName))) {
      throw new MobileHarnessException(
          ExtErrorId.MODULE_CONFIG_DRIVER_NOT_MATCH,
          String.format(
              "The module requires driver %s but %s is used for the job.",
              driverName, jobInfo.type().getDriver()));
    }

    ScopedSpecs jobScopedSpecs = jobInfo.scopedSpecs();
    Optional<Entry<String, JsonObject>> scopedSpec =
        convertOptionsToScopedSpec(driverName, config.getOptionsList());
    scopedSpec.ifPresent(spec -> jobScopedSpecs.add(spec.getKey(), spec.getValue()));
    jobScopedSpecs.addAll(
        JobSpecWalker.resolve(
            jobScopedSpecs.toJobSpec(JobSpecHelper.getDefaultHelper()), fileResolver));
  }

  private void updateDeviceSpecs(
      List<Device> configs,
      @Nullable ModuleDeviceConfiguration moduleDeviceConfig,
      SubDeviceSpecs subDeviceSpecs,
      Visitor fileResolver)
      throws MobileHarnessException, InterruptedException {
    Optional<DeviceGroup> validDeviceGroup =
        getValidDeviceGroup(moduleDeviceConfig, configs.size());

    if (configs.size() != subDeviceSpecs.getSubDeviceCount()) {
      throw new MobileHarnessException(
          ExtErrorId.MODULE_CONFIG_DEVICE_NUMBER_NOT_MATCH,
          String.format(
              "The module requires %d but %d are provided.",
              configs.size(), subDeviceSpecs.getSubDeviceCount()));
    }
    // Adds decorators.
    addDecorators(configs, subDeviceSpecs.getAllSubDevices(), fileResolver);

    // Adds dimensions.
    for (int i = 0; i < configs.size(); i++) {
      final int index = i;
      addDimensions(
          configs.get(i),
          subDeviceSpecs.getSubDevice(i),
          validDeviceGroup.map(group -> group.getDeviceId(index)).orElse(null));
    }
  }

  private void addDimensions(
      Device config, SubDeviceSpec subDeviceSpec, @Nullable String deviceId) {
    ImmutableMap<String, String> dimensions =
        config.getOptionsList().stream()
            .filter(option -> option.getName().equals(DEVICE_DIMENSION_OPTION_NAME))
            .collect(toImmutableMap(Option::getKey, Option::getValue));
    Dimensions deviceDimensions = subDeviceSpec.deviceRequirement().dimensions();
    deviceDimensions.addAll(dimensions);
    if (deviceId != null && !anyDeviceId(deviceId)) {
      deviceDimensions.add(Name.ID, deviceId);
    }
  }

  private String resolveFilePath(String fileName, List<File> dependencies)
      throws MobileHarnessException {
    for (File dependency : dependencies) {
      if (dependency.isFile()) {
        if (dependency.getName().equals(fileName)) {
          return dependency.getAbsolutePath();
        }
      } else {
        Optional<File> file = configurationUtil.getFileInDir(fileName, dependency);
        if (file.isPresent()) {
          return file.get().getAbsolutePath();
        }
      }
    }
    throw new MobileHarnessException(
        BasicErrorId.LOCAL_FILE_OR_DIR_NOT_FOUND,
        String.format("Cannot find test artifact %s", fileName));
  }

  private Optional<Entry<String, JsonObject>> convertOptionsToScopedSpec(
      String driverOrDecoratorName, List<Option> options) throws MobileHarnessException {
    Optional<String> specName =
        DriverDecoratorSpecMapper.getSpecNameByDriverOrDecorator(driverOrDecoratorName);
    if (specName.isEmpty()) {
      return Optional.empty();
    }
    Optional<Class<? extends Message>> specClass =
        JobSpecHelper.getDefaultHelper().getRegisteredExtensionClasses().stream()
            .filter(clazz -> clazz.getSimpleName().equals(specName.get()))
            .findFirst();
    if (specClass.isEmpty()) {
      return Optional.empty();
    }

    JsonObject spec = new JsonObject();
    Descriptor specDescriptor =
        JobSpecHelper.getDefaultInstance(specClass.get()).getDescriptorForType();
    for (Option option : options) {
      FieldDescriptor fieldDescriptor = specDescriptor.findFieldByName(option.getName());
      if (fieldDescriptor == null) {
        throw new MobileHarnessException(
            ExtErrorId.MODULE_CONFIG_UNRECOGNIZED_OPTION_ERROR,
            String.format("Unrecognized option %s for %s", option.getName(), specName));
      }
      if (fieldDescriptor.isRepeated()) {
        JsonArray jsonArray =
            spec.has(option.getName()) ? spec.getAsJsonArray(option.getName()) : new JsonArray();
        jsonArray.add(option.getValue());
        spec.add(option.getName(), jsonArray);
      } else {
        spec.addProperty(option.getName(), option.getValue());
      }
    }
    return Optional.of(Map.entry(specClass.get().getSimpleName(), spec));
  }

  private void addDecorators(
      List<Device> configs, List<SubDeviceSpec> subDeviceSpecs, Visitor fileResolver)
      throws MobileHarnessException, InterruptedException {
    for (int i = 0; i < configs.size(); i++) {
      Device deviceConfig = configs.get(i);
      SubDeviceSpec subDeviceSpec = subDeviceSpecs.get(i);
      ScopedSpecs deviceScopedSpecs = subDeviceSpec.scopedSpecs();
      // Add decorators and scoped specs
      List<String> decorators = new ArrayList<>();
      for (TargetPreparer targetPreparer : deviceConfig.getTargetPreparersList()) {
        String decoratorName = ConfigurationUtil.getSimpleClassName(targetPreparer.getClazz());
        decorators.add(decoratorName);
        if (!targetPreparer.getOptionsList().isEmpty()) {
          Optional<Entry<String, JsonObject>> scopedSpec =
              convertOptionsToScopedSpec(decoratorName, targetPreparer.getOptionsList());
          scopedSpec.ifPresent(spec -> deviceScopedSpecs.add(spec.getKey(), spec.getValue()));
        }
      }
      deviceScopedSpecs.addAll(
          JobSpecWalker.resolve(
              deviceScopedSpecs.toJobSpec(JobSpecHelper.getDefaultHelper()), fileResolver));
      subDeviceSpec.decorators().addAll(decorators);
    }
  }

  private static Optional<DeviceGroup> getValidDeviceGroup(
      @Nullable ModuleDeviceConfiguration moduleDeviceConfig, int requiredDeviceCount)
      throws MobileHarnessException {
    if (moduleDeviceConfig == null) {
      return Optional.empty();
    }
    if (moduleDeviceConfig.getDeviceGroupCount() == 0) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.XTS_DEVICE_CONFIG_FILE_VALIDATE_ERROR,
          String.format(
              "Zero device group in module device config [%s]",
              shortDebugString(moduleDeviceConfig)),
          /* cause= */ null);
    }
    // Always gets the first group for now.
    DeviceGroup deviceGroup = moduleDeviceConfig.getDeviceGroup(0);
    List<String> deviceIds = deviceGroup.getDeviceIdList();
    if (deviceIds.size() != requiredDeviceCount) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.XTS_DEVICE_CONFIG_FILE_VALIDATE_ERROR,
          String.format(
              "Require %s devices but found %s in module device config [%s]",
              requiredDeviceCount, deviceIds.size(), shortDebugString(moduleDeviceConfig)),
          /* cause= */ null);
    }
    return Optional.of(deviceGroup);
  }

  private static boolean anyDeviceId(String deviceId) {
    return deviceId.equals(ANY_DEVICE_ID);
  }
}
