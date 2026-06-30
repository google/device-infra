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

package com.google.wireless.qa.mobileharness.shared.model.job.in.json;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.protobuf.MessageOrBuilder;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidFilePullerDecoratorSpec;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProtocolMessageOrBuilderJsonSerializerTest {

  private Gson gson;

  @Before
  public void setUp() {
    gson =
        new GsonBuilder()
            .registerTypeHierarchyAdapter(
                MessageOrBuilder.class, new ProtocolMessageOrBuilderJsonSerializer())
            .create();
  }

  @Test
  public void testMapField_serializeAndDeserialize() {
    AndroidFilePullerDecoratorSpec spec =
        AndroidFilePullerDecoratorSpec.newBuilder()
            .putProperty("test_key", "test_value")
            .putProperty("test_key2", "test_value2")
            .build();

    String json = gson.toJson(spec);

    AndroidFilePullerDecoratorSpec deserializedSpec =
        gson.fromJson(json, AndroidFilePullerDecoratorSpec.class);

    assertThat(deserializedSpec.getPropertyMap())
        .containsExactly("test_key", "test_value", "test_key2", "test_value2");
  }
}
