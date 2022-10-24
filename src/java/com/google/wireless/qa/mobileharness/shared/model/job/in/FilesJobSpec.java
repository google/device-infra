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

package com.google.wireless.qa.mobileharness.shared.model.job.in;


import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Message;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecHelper;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecWrapper;
import com.google.wireless.qa.mobileharness.shared.proto.spec.BaseSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.JobSpec;
import java.util.ArrayList;
import java.util.List;

/**
 * A JobSpec wrapper with {@link Files}. It use the contents of a {@link Files} to fill any file
 * field in a JobSpec.
 */
public class FilesJobSpec implements JobSpecWrapper {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** default file values of JobSpec. */
  private final Files files;

  /**
   * Creates a wrapper of {@code jobSpec} with default value {@code files}.
   *
   * @param files The key is field name, while the value is the string value of field. If the value
   *     is not a string or a list of string, it will be ignored.
   */
  public FilesJobSpec(Files files) {
    this.files = files;
  }

  private void fillFileField(FieldDescriptor field, Message.Builder builder) {
    ImmutableSet<String> paths = files.get(field.getName());
    if (paths == null || paths.isEmpty()) {
      return;
    }

    if (!field.getJavaType().equals(JavaType.STRING)) {
      logger.atInfo().log(
          "File field is not a String or a List of String: %s", field.getFullName());
      return;
    }

    List<String> pathList = new ArrayList<>(paths);
    if (field.isRepeated()) {
      builder.setField(field, pathList);
    } else {
      builder.setField(field, pathList.get(0));
    }
  }

  /**
   * Gets spec data of class {@code specClass} from wrapped data.
   *
   * @throws MobileHarnessException if {@code specClass} is not a valid extension of {@link JobSpec}
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T extends Message> T getSpec(Class<T> specClass) throws MobileHarnessException {
    Message.Builder builder = null;
    try {
      builder = JobSpecHelper.getDefaultInstance(specClass).newBuilderForType();
    } catch (IllegalArgumentException e) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_SPEC_PARSE_PROTOBUF_ERROR, "Invalid Message class: " + specClass, e);
    }
    for (FieldDescriptor field : builder.getDescriptorForType().getFields()) {
      if (field.getOptions().hasExtension(BaseSpec.fileDetail)) {
        fillFileField(field, builder);
      }
    }
    return (T) builder.build();
  }
}
