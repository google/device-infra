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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Device;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Option;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.TargetPreparer;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Test;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.wireless.qa.mobileharness.shared.api.ClassUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.ScopedSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecHelper;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecWalker;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecWalker.Visitor;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

/** A Helper class for xTS module configuration. */
public class ModuleConfigurationHelper {
  private static final String FILE_KEY = "file";

  private final ConfigurationUtil configurationUtil;

  public ModuleConfigurationHelper() {
    this.configurationUtil = new ConfigurationUtil();
  }

  /**
   * Updates JobInfo based on the given module configuration.
   *
   * @param jobInfo the {@link JobInfo} to update. The type of driver and device need to be correct.
   * @param moduleConfig the {@link Configuration} for the module.
   * @param dependencyDirs the list of {@link File} for directories that contain dependency files.
   *     Search files in these directories in order.
   */
  public void updateJobInfo(JobInfo jobInfo, Configuration moduleConfig, List<File> dependencyDirs)
      throws MobileHarnessException, InterruptedException {
    for (Option option : moduleConfig.getOptionsList()) {
      if (option.getKey().equals(FILE_KEY)) {
        jobInfo.files().add(option.getName(), resolveFilePath(option.getValue(), dependencyDirs));
      } else {
        jobInfo.params().add(option.getName(), option.getValue());
      }
    }

    Visitor fileResolver = getFileResolver(dependencyDirs);
    updateDriverSpecs(moduleConfig.getTest(), jobInfo, fileResolver);
    updateDeviceSpecs(moduleConfig.getDevicesList(), jobInfo.subDeviceSpecs(), fileResolver);
  }

  private Visitor getFileResolver(List<File> dependencyDirs) {
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
                resolveFilePath((String) builder.getRepeatedField(field, i), dependencyDirs));
          }
        } else {
          builder.setField(
              field, resolveFilePath((String) builder.getField(field), dependencyDirs));
        }
      }
    };
  }

  private void updateDriverSpecs(Test config, JobInfo jobInfo, Visitor fileResolver)
      throws MobileHarnessException, InterruptedException {
    String className = ConfigurationUtil.getSimpleClassName(config.getClazz());
    if (!jobInfo.type().getDriver().equals(className)) {
      throw new MobileHarnessException(
          ExtErrorId.MODULE_CONFIG_DRIVER_NOT_MATCH,
          String.format(
              "The module requires driver %s but %s is used for the job.",
              className, jobInfo.type().getDriver()));
    }

    Class<?> clazz = ClassUtil.getDriverClass(className);
    Entry<String, JsonObject> scopedSpec =
        convertOptionsToScopedSpec(clazz, config.getOptionsList());
    ScopedSpecs jobScopedSpecs = jobInfo.scopedSpecs();
    jobScopedSpecs.add(scopedSpec.getKey(), scopedSpec.getValue());
    jobScopedSpecs.addAll(
        JobSpecWalker.resolve(
            jobScopedSpecs.toJobSpec(JobSpecHelper.getDefaultHelper()), fileResolver));
  }

  private void updateDeviceSpecs(
      List<Device> configs, SubDeviceSpecs subDeviceSpecs, Visitor fileResolver)
      throws MobileHarnessException, InterruptedException {
    ListMultimap<String, Device> configMap = ArrayListMultimap.create();
    ListMultimap<String, SubDeviceSpec> subDeviceSpecMap = ArrayListMultimap.create();

    for (Device device : configs) {
      configMap.put(device.getName(), device);
    }
    for (SubDeviceSpec subDeviceSpec : subDeviceSpecs.getAllSubDevices()) {
      subDeviceSpecMap.put(subDeviceSpec.type(), subDeviceSpec);
    }

    for (String key : configMap.keySet()) {
      List<Device> configList = configMap.get(key);
      List<SubDeviceSpec> subDeviceSpecList = subDeviceSpecMap.get(key);
      if (configList.size() != subDeviceSpecList.size()) {
        throw new MobileHarnessException(
            ExtErrorId.MODULE_CONFIG_DEVICE_NUMBER_NOT_MATCH,
            String.format(
                "The module requires %d %s but %d %s are provided.",
                configList.size(), key, subDeviceSpecList.size(), key));
      }
      addDecorators(configMap.get(key), subDeviceSpecMap.get(key), fileResolver);
    }
  }

  private String resolveFilePath(String fileName, List<File> dirs) throws MobileHarnessException {
    for (File dir : dirs) {
      Optional<File> file = configurationUtil.getFileInDir(fileName, dir);
      if (file.isPresent()) {
        return file.get().getAbsolutePath();
      }
    }
    throw new MobileHarnessException(
        BasicErrorId.LOCAL_FILE_OR_DIR_NOT_FOUND,
        String.format("Cannot find test artifact %s", fileName));
  }

  private Entry<String, JsonObject> convertOptionsToScopedSpec(Class<?> clazz, List<Option> options)
      throws MobileHarnessException {
    if (!JobSpecHelper.isSpecConfigable(clazz)) {
      Map<String, String> params = new HashMap<>();
      for (Option option : options) {
        params.put(option.getKey(), option.getValue());
      }
      return Map.entry(clazz.getName(), new Gson().toJsonTree(params).getAsJsonObject());
    }

    JsonObject spec = new JsonObject();
    Class<? extends Message> specClass = JobSpecHelper.getSpecClass(clazz);
    Descriptor specDescriptor = JobSpecHelper.getDefaultInstance(specClass).getDescriptorForType();
    for (Option option : options) {
      FieldDescriptor fieldDescriptor = specDescriptor.findFieldByName(option.getName());
      if (fieldDescriptor == null) {
        throw new MobileHarnessException(
            ExtErrorId.MODULE_CONFIG_UNRECOGNIZED_OPTION_ERROR,
            String.format("Unrecognized option %s for %s", option.getName(), clazz.getName()));
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
    return Map.entry(specClass.getSimpleName(), spec);
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
        String className = ConfigurationUtil.getSimpleClassName(targetPreparer.getClazz());
        decorators.add(className);
        if (!targetPreparer.getOptionsList().isEmpty()) {
          Class<?> clazz = ClassUtil.getDecoratorClass(className);
          Entry<String, JsonObject> scopedSpec =
              convertOptionsToScopedSpec(clazz, targetPreparer.getOptionsList());
          deviceScopedSpecs.add(scopedSpec.getKey(), scopedSpec.getValue());
        }
      }
      deviceScopedSpecs.addAll(
          JobSpecWalker.resolve(
              deviceScopedSpecs.toJobSpec(JobSpecHelper.getDefaultHelper()), fileResolver));
      subDeviceSpec.decorators().addAll(decorators);
    }
  }
}
