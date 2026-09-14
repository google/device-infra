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

import com.google.common.collect.ImmutableMap;
import com.google.devtools.common.metrics.stability.model.proto.ErrorIdProto.ErrorId;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.common.metrics.stability.model.proto.NamespaceProto.Namespace;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DeserializedExceptionTest {

  private static final DeserializedErrorId ERROR_ID =
      DeserializedErrorId.of(
          ErrorId.newBuilder()
              .setCode(1234)
              .setName("TEST_ERROR")
              .setType(ErrorType.INFRA_ISSUE)
              .setNamespace(Namespace.MH)
              .build());

  @Test
  public void metadata_addAndGet() {
    DeserializedException exception =
        new DeserializedException(ERROR_ID, "Test error message", "com.example.CustomException");

    assertThat(exception.getMetadata()).isEmpty();
    assertThat(exception.getMetadata("key1")).isEmpty();

    exception.addMetadata("key1", "value1");
    assertThat(exception.getMetadata()).containsExactly("key1", "value1");
    assertThat(exception.getMetadata("key1")).hasValue("value1");

    exception.addMetadata(ImmutableMap.of("key2", "value2", "key3", "value3"));
    assertThat(exception.getMetadata())
        .containsExactly("key1", "value1", "key2", "value2", "key3", "value3");
    assertThat(exception.getMetadata("key2")).hasValue("value2");
    assertThat(exception.getMetadata("nonexistent")).isEmpty();
  }

  @Test
  public void toString_withoutMetadata() {
    DeserializedException customException =
        new DeserializedException(ERROR_ID, "Test error message", "com.example.CustomException");
    assertThat(customException.toString())
        .isEqualTo("com.example.CustomException: Test error message [CustomException]");

    DeserializedException mhException =
        new DeserializedException(
            ERROR_ID,
            "MH error message",
            "com.google.devtools.mobileharness.api.model.error.MobileHarnessException");
    assertThat(mhException.toString())
        .isEqualTo("MobileHarnessException: MH error message [MobileHarnessException]");
  }

  @Test
  public void toString_withMetadata() {
    DeserializedException exception =
        new DeserializedException(ERROR_ID, "Test error message", "com.example.CustomException");
    exception.addMetadata("key1", "value1");
    exception.addMetadata("key2", "value2");

    assertThat(exception.toString())
        .isEqualTo(
            "com.example.CustomException: Test error message [CustomException],"
                + " metadata={key1=value1, key2=value2}");
  }
}
