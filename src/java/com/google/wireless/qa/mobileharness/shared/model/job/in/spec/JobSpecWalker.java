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

package com.google.wireless.qa.mobileharness.shared.model.job.in.spec;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.protobuf.DescriptorProtos.FieldOptions;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.Type;
import com.google.protobuf.Message;
import com.google.protobuf.UnknownFieldSet;
import com.google.wireless.qa.mobileharness.shared.proto.spec.BaseSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.JobSpec;

/** A walker of {@link JobSpec}. */
public class JobSpecWalker {

  /** A visitor of {@link JobSpec}. */
  public static class Visitor {

    /**
     * Resolves a normal field marked by {@link FieldDetail}.
     *
     * @param builder Parent message builder of {@code field}
     * @param field descriptor of visited field
     */
    public void visitParamField(Message.Builder builder, FieldDescriptor field)
        throws MobileHarnessException, InterruptedException {}

    /**
     * Resolves a primitive field marked by {@link FileDetail}.
     *
     * @param builder Parent message builder of {@code field}
     * @param field descriptor of visited field
     */
    public void visitPrimitiveFileField(Message.Builder builder, FieldDescriptor field)
        throws MobileHarnessException, InterruptedException {}

    /**
     * Resolves unknown fields. It is called only if {@code unknownFields} is not empty.
     *
     * @param builder Parent message builder of {@code unknownFields}
     * @param unknownFields Unknown field set of {@code builder}
     */
    public void visitUnknownFields(Message.Builder builder, UnknownFieldSet unknownFields)
        throws MobileHarnessException, InterruptedException {}
  }

  /**
   * Walks through each field/files of {@code spec} with {@code resolver}, returns a new JobSpec
   * after resolving.
   */
  public static JobSpec resolve(JobSpec spec, Visitor resolver)
      throws MobileHarnessException, InterruptedException {
    if (spec.equals(JobSpec.getDefaultInstance())) {
      return spec;
    }
    JobSpec.Builder newSpec = spec.toBuilder();
    new JobSpecWalker().walkMessage(newSpec, resolver);
    return newSpec.build();
  }

  private JobSpecWalker() {}

  private void walkExtension(
      Message.Builder builder, FieldDescriptor extensionField, Visitor visitor)
      throws MobileHarnessException, InterruptedException {
    if (extensionField.getType() == Type.MESSAGE && !extensionField.isRepeated()) {
      Message.Builder extensionBuilder = ((Message) builder.getField(extensionField)).toBuilder();
      walkMessage(extensionBuilder, visitor);
      builder.setField(extensionField, extensionBuilder.build());
    } // else: Drop all repeated extensions or simple type extensions.
  }

  private void walkRepeatedMessage(Message.Builder builder, FieldDescriptor field, Visitor visitor)
      throws MobileHarnessException, InterruptedException {
    int count = builder.getRepeatedFieldCount(field);
    for (int i = 0; i < count; i++) {
      walkMessage(builder.getRepeatedFieldBuilder(field, i), visitor);
    }
  }

  private void walkField(Message.Builder builder, FieldDescriptor field, Visitor visitor)
      throws MobileHarnessException, InterruptedException {
    FieldOptions options = field.getOptions();
    if (options.hasExtension(BaseSpec.fileDetail)) {
      visitor.visitPrimitiveFileField(builder, field);
    } else {
      // If a field is not marked by any detail(See Detail in
      // http://cs?q=mobileharness+file:base_spec.proto), it is treated as a field with empty
      // {@link #FieldDetail}.
      visitor.visitParamField(builder, field);
    }
  }

  private void walkMessage(Message.Builder builder, Visitor visitor)
      throws MobileHarnessException, InterruptedException {
    for (FieldDescriptor field : builder.getAllFields().keySet()) {
      if (field.isExtension()) {
        walkExtension(builder, field, visitor);
      } else {
        if (field.getType() == Type.MESSAGE) {
          if (field.isRepeated()) {
            walkRepeatedMessage(builder, field, visitor);
          } else {
            walkMessage(builder.getFieldBuilder(field), visitor);
          }
        } else {
          walkField(builder, field, visitor);
        }
      }
    }

    UnknownFieldSet unknownFields = builder.getUnknownFields();
    if (!unknownFields.equals(UnknownFieldSet.getDefaultInstance())) {
      visitor.visitUnknownFields(builder, unknownFields);
    }
  }
}
