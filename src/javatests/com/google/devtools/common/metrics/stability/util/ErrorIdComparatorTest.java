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

package com.google.devtools.common.metrics.stability.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.common.metrics.stability.model.ErrorId;
import com.google.devtools.common.metrics.stability.model.proto.ErrorIdProto;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.common.metrics.stability.model.proto.NamespaceProto.Namespace;
import com.google.devtools.deviceinfra.api.error.id.defined.BasicErrorId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ErrorIdComparatorTest {
  @Test
  public void equals_compareClasses() {
    assertThat(
            ErrorIdComparator.equal(
                BasicErrorId.REFLECTION_LOAD_CLASS_ERROR,
                BasicErrorId.REFLECTION_LOAD_CLASS_TYPE_MISMATCH))
        .isFalse();
    assertThat(
            ErrorIdComparator.equal(
                BasicErrorId.REFLECTION_LOAD_CLASS_ERROR, BasicErrorId.REFLECTION_LOAD_CLASS_ERROR))
        .isTrue();
    assertThat(
            ErrorIdComparator.equal(
                BasicErrorId.REFLECTION_LOAD_CLASS_ERROR,
                new ErrorId() {
                  @Override
                  public int code() {
                    return BasicErrorId.REFLECTION_LOAD_CLASS_ERROR.code();
                  }

                  @Override
                  public String name() {
                    return BasicErrorId.REFLECTION_LOAD_CLASS_ERROR.name();
                  }

                  @Override
                  public ErrorType type() {
                    return BasicErrorId.REFLECTION_LOAD_CLASS_ERROR.type();
                  }

                  @Override
                  public Namespace namespace() {
                    return BasicErrorId.REFLECTION_LOAD_CLASS_ERROR.namespace();
                  }
                }))
        .isTrue();
  }

  @Test
  public void equals_compareProto() {
    BasicErrorId mhErrorId = BasicErrorId.REFLECTION_LOAD_CLASS_ERROR;
    ErrorIdProto.ErrorId commonErrorIdProto = ErrorModelConverter.toErrorIdProto(mhErrorId);
    assertThat(ErrorIdComparator.equal(commonErrorIdProto, mhErrorId)).isTrue();
    assertThat(ErrorIdComparator.equal(mhErrorId, commonErrorIdProto)).isTrue();

    assertThat(ErrorIdComparator.equal(commonErrorIdProto.toBuilder().clearNamespace(), mhErrorId))
        .isFalse();
    assertThat(
            ErrorIdComparator.equal(
                commonErrorIdProto.toBuilder().setName("A_DIFFERENT_NAME"), mhErrorId))
        .isFalse();
  }
}
