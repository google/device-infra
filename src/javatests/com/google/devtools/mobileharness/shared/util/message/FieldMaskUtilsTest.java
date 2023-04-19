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

package com.google.devtools.mobileharness.shared.util.message;

import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetSessionResponse;
import com.google.protobuf.FieldMask;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FieldMaskUtilsTest {

  @Test
  public void subFieldMask() throws Exception {
    assertThat(
            FieldMaskUtils.subFieldMask(
                    FieldMask.newBuilder()
                        .addPaths("whatever")
                        .addPaths("session_detail.session_outputwhatever")
                        .addPaths("session_detail.session_output.session_property")
                        .addPaths(
                            "session_detail.session_output.session_plugin_error.plugin_class_name")
                        .build(),
                    GetSessionResponse.getDescriptor()
                        .findFieldByNumber(GetSessionResponse.SESSION_DETAIL_FIELD_NUMBER),
                    SessionDetail.getDescriptor()
                        .findFieldByNumber(SessionDetail.SESSION_OUTPUT_FIELD_NUMBER))
                .orElseThrow())
        .isEqualTo(
            FieldMask.newBuilder()
                .addPaths("session_property")
                .addPaths("session_plugin_error.plugin_class_name")
                .build());

    assertThat(
            FieldMaskUtils.subFieldMask(
                FieldMask.newBuilder()
                    .addPaths("whatever")
                    .addPaths("session_detail.session_output")
                    .addPaths(
                        "session_detail.session_output.session_plugin_error.plugin_class_name")
                    .build(),
                GetSessionResponse.getDescriptor()
                    .findFieldByNumber(GetSessionResponse.SESSION_DETAIL_FIELD_NUMBER),
                SessionDetail.getDescriptor()
                    .findFieldByNumber(SessionDetail.SESSION_OUTPUT_FIELD_NUMBER)))
        .isEmpty();
  }
}
