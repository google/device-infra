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

package com.google.devtools.mobileharness.shared.util.error;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.common.metrics.stability.model.proto.ErrorIdProto.ErrorId;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionSummary;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.FlattenedExceptionDetail;
import com.google.wireless.qa.mobileharness.shared.proto.Common.ErrorInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ErrorModelConverterTest {

  @Test
  public void toLegacyErrorInfo_fromExceptionDetail_preservesStackTrace() {
    ExceptionDetail detail =
        ExceptionDetail.newBuilder()
            .setSummary(
                ExceptionSummary.newBuilder()
                    .setMessage("test message")
                    .setErrorId(ErrorId.newBuilder().setName("test_error").setCode(123).build())
                    .build())
            .build();

    ErrorInfo errorInfo = ErrorModelConverter.toLegacyErrorInfo(detail);

    assertThat(errorInfo.getStackTrace()).isNotEmpty();
    assertThat(errorInfo.getMessage()).isEqualTo("test message");
  }

  @Test
  public void toLegacyErrorInfo_fromFlattenedExceptionDetail_preservesStackTrace() {
    FlattenedExceptionDetail detail =
        FlattenedExceptionDetail.newBuilder()
            .setSummary(
                ExceptionSummary.newBuilder()
                    .setMessage("test message")
                    .setErrorId(ErrorId.newBuilder().setName("test_error").setCode(123).build())
                    .build())
            .setCompleteStackTrace("fake stack trace")
            .build();

    ErrorInfo errorInfo = ErrorModelConverter.toLegacyErrorInfo(detail);

    assertThat(errorInfo.getStackTrace()).isEqualTo("fake stack trace");
    assertThat(errorInfo.getMessage()).isEqualTo("test message");
  }
}
