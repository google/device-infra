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

package com.google.devtools.common.metrics.stability.converter;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.common.metrics.stability.model.proto.ErrorIdProto.ErrorId;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.common.metrics.stability.model.proto.NamespaceProto.Namespace;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeserializedErrorIdTest {

  @Test
  public void hasLegalToString() {
    DeserializedErrorId errorId =
        DeserializedErrorId.of(
            ErrorId.newBuilder()
                .setCode(1234)
                .setName("FOO")
                .setType(ErrorType.CUSTOMER_ISSUE)
                .setNamespace(Namespace.MH)
                .build());
    assertThat(
            String.format(
                "[%s|%s|%s|%s]",
                errorId.namespace(), errorId.type(), errorId.name(), errorId.code()))
        .isEqualTo(errorId.toString());
  }
}
