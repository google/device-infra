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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Message;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecHelper;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecWrapper;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.spec.BaseSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.JobSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A JobSpec wrapper with {@link Params}. It use the contents of a {@link Params} to fill any param
 * field in a JobSpec.
 */
public class ParamsJobSpec implements JobSpecWrapper {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** default param values of JobSpec. */
  private final Params params;

  private final boolean partialBuild;

  /** Creates an empty wrapper. */
  public ParamsJobSpec() {
    this(new Params((Timing) null), false);
  }

  /** Creating a wrapper with only params. */
  public ParamsJobSpec(Params params) {
    this(params, false);
  }

  /**
   * Creates a wrapper of {@code jobSpec} with default value {@code defaultValues}.
   *
   * @param params key is field name, while value is the string value of field; The value with
   *     invalid form will be ignored, such as "1234" for a boolean field.
   * @param partialBuild If true, do a partial build to ignore required field checking.
   */
  public ParamsJobSpec(Params params, boolean partialBuild) {
    this.params = params;
    this.partialBuild = partialBuild;
  }

  private void fillParamField(FieldDescriptor field, Message.Builder builder) {
    String defaultValue = params.get(field.getName());
    if (Strings.isNullOrEmpty(defaultValue)) {
      return;
    }
    JavaType fieldJavaType = field.getJavaType();
    if (field.isRepeated()) {
      if (builder.getRepeatedFieldCount(field) <= 0) {
        List<Object> values = new ArrayList<>();
        for (String strValue : StrUtil.toList(defaultValue)) {
          Optional<Object> value = valueOf(fieldJavaType, strValue, field);
          if (value.isPresent()) {
            values.add(value.get());
          } else {
            logger.atInfo().log(
                "Can't assign \"%s\" to field %s because of incompatible type. Required %s",
                strValue, field.getFullName(), field.getJavaType());
          }
        }
        if (!values.isEmpty()) {
          builder.setField(field, values);
        }
      }
    } else {
      if (!builder.hasField(field)) {
        Optional<Object> value = valueOf(fieldJavaType, defaultValue, field);
        if (value.isPresent()) {
          builder.setField(field, value.get());
        } else {
          logger.atInfo().log(
              "Can't assign \"%s\" to field %s because of incompatible type. Required %s",
              defaultValue, field.getFullName(), field.getJavaType());
        }
      }
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
    Message.Builder builder;
    try {
      builder = JobSpecHelper.getDefaultInstance(specClass).newBuilderForType();
    } catch (IllegalArgumentException e) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_SPEC_PARSE_PROTOBUF_ERROR, "Invalid Message class: " + specClass, e);
    }

    for (FieldDescriptor field : builder.getDescriptorForType().getFields()) {
      if (!field.getOptions().hasExtension(BaseSpec.fileDetail)) {
        fillParamField(field, builder);
      }
    }

    if (!partialBuild) {
      return (T) builder.build();
    } else {
      return (T) builder.buildPartial();
    }
  }

  /**
   * Converts {@code strValue} to the proper class according to {@code type}.
   *
   * @param type java type of {@code strValue}
   * @param strValue string value to convert
   * @param field field descriptor; it is only used when {@code type} is {@link JavaType#ENUM}
   * @return converted value of {@code type}; or {@link Optional#empty} if it is failed or {@code
   *     strValue} is null
   */
  @VisibleForTesting
  Optional<Object> valueOf(
      JavaType type, @Nullable String strValue, @Nullable FieldDescriptor field) {
    if (strValue == null) {
      return Optional.empty();
    }
    try {
      switch (type) {
        case INT:
          return Optional.of(Integer.valueOf(strValue));
        case LONG:
          return Optional.of(Long.valueOf(strValue));
        case FLOAT:
          return Optional.of(Float.valueOf(strValue));
        case DOUBLE:
          return Optional.of(Double.valueOf(strValue));
        case BOOLEAN:
          if (Ascii.equalsIgnoreCase(strValue, "true")) {
            return Optional.of(true);
          } else if (Ascii.equalsIgnoreCase(strValue, "false")) {
            return Optional.of(false);
          } else {
            return Optional.empty();
          }
        case STRING:
          return Optional.of(strValue);
        case ENUM:
          return Optional.ofNullable(field.getEnumType().findValueByName(strValue));
        case BYTE_STRING:
          return Optional.of(ByteString.copyFromUtf8(strValue));
        case MESSAGE:
      }
      return Optional.empty();
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }
}
