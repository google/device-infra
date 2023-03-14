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

import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfigs;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder of {@link com.google.wireless.qa.mobileharness.shared.proto.JobConfigs}, provides a set
 * of handy methods for it.
 */
public class JobConfigsBuilder {

  private final List<JobConfigBuilder> configs = new ArrayList<>();

  private JobConfigsBuilder(JobConfigs configs) {
    for (JobConfig config : configs.getJobConfigList()) {
      this.configs.add(JobConfigBuilder.fromProto(config));
    }
  }

  /**
   * Creates {@link JobConfigsBuilder} with the default {@link JobConfigs} instance as the initial
   * value. It is a shortcut of {@code fromProto(JobConfigs.getDefaultInstance())}.
   */
  public static JobConfigsBuilder fromDefaultProto() {
    return new JobConfigsBuilder(JobConfigs.getDefaultInstance());
  }

  /** Creates {@link JobConfigsBuilder} from protobuf {@code configs}. */
  public static JobConfigsBuilder fromProto(JobConfigs configs) {
    return new JobConfigsBuilder(configs);
  }

  /** Creates {@link JobConfigsBuilder} from json string in file {@code path}. */
  public static JobConfigsBuilder fromJsonFile(String path) throws MobileHarnessException {
    return fromJson(new LocalFileUtil().readFile(path));
  }

  /** Creates {@link JobConfigsBuilder} from json string {@code json}. */
  public static JobConfigsBuilder fromJson(String json) throws MobileHarnessException {
    try {
      /**
       * Reuse the json config re-writing in JobConfigBuilder#fromJson below instead of directly
       * attempting serialization of the JobConfigs object. JobConfigBuilder#fromJson actually
       * checks for and rewrites the deprecated json format (String device, Map dimensions, List
       * decorator args) to the new format (device contains a list of (String device type, Map
       * dimensions, List decorators with or without scope specs) tuples.
       */
      JobConfigs.Builder jobConfigsBuilder = JobConfigs.newBuilder();
      JsonArray jsonConfigs =
          ((JsonObject) JsonParser.parseString(json)).getAsJsonArray("job_config");
      for (JsonElement config : jsonConfigs) {
        jobConfigsBuilder.addJobConfig(JobConfigBuilder.fromJson(config.toString()).build());
      }
      return new JobConfigsBuilder(jobConfigsBuilder.build());
    } catch (JsonParseException e) {
      throw new MobileHarnessException(
          ErrorCode.JOB_CONFIG_ERROR,
          "Json string is not a valid job config: " + JobConfigGsonHolder.prettyJson(json),
          e);
    }
  }

  public String toJson() throws MobileHarnessException {
    JobConfigs proto = build();
    try {
      return JobConfigGsonHolder.getGson().toJson(proto, JobConfigs.class);
    } catch (JsonParseException e) {
      throw new MobileHarnessException(
          ErrorCode.JOB_CONFIG_ERROR,
          "Failed to serialize proto to json. proto message: " + proto,
          e);
    }
  }

  /** Gets item list. */
  public List<JobConfigBuilder> getList() {
    return configs;
  }

  /** Gets {@code i}th item. */
  public JobConfigBuilder get(int i) {
    return configs.get(i);
  }

  /** Adds a new item to the end. */
  public JobConfigBuilder add() {
    JobConfigBuilder config = JobConfigBuilder.fromDefaultProto();
    configs.add(config);
    return config;
  }

  @CanIgnoreReturnValue
  public JobConfigsBuilder clear() {
    configs.clear();
    return this;
  }

  /** Size of JobConfig list. */
  public int size() {
    return configs.size();
  }

  /** Whether JobConfigs is empty. */
  public boolean isEmpty() {
    return configs.isEmpty();
  }

  /**
   * Merges with {@code other}. Does nothing if {@code other} is empty.
   *
   * @throws MobileHarnessException if {@code other} doesn't have the same size of {@code this}.
   */
  @CanIgnoreReturnValue
  public JobConfigsBuilder merge(JobConfigsBuilder other) throws MobileHarnessException {
    if (other.isEmpty()) {
      return this;
    }
    if (isEmpty()) {
      for (JobConfigBuilder config : other.getList()) {
        this.add().merge(config);
      }
    } else {
      int size = size();
      if (other.size() != size) {
        throw new MobileHarnessException(
            ErrorCode.JOB_CONFIG_ERROR,
            String.format(
                "Failed to merge JobConfigsBuilder with size [%s], expected size is [%s]",
                other.size(), size));
      }
      for (int i = 0; i < size; i++) {
        this.configs.get(i).merge(other.configs.get(i));
      }
    }
    return this;
  }

  /** Validates all items. Throws exception if any items is invalid. */
  public void validate() throws MobileHarnessException {
    for (JobConfigBuilder config : configs) {
      config.validate();
    }
  }

  /** Builds {@link JobConfigs}. */
  public JobConfigs build() {
    JobConfigs.Builder proto = JobConfigs.newBuilder();
    for (JobConfigBuilder config : configs) {
      proto.addJobConfig(config.build());
    }
    return proto.build();
  }

  @Override
  public String toString() {
    try {
      // Use json first, which is more compact.
      return toJson();
    } catch (MobileHarnessException e) {
      return configs.toString();
    }
  }
}
