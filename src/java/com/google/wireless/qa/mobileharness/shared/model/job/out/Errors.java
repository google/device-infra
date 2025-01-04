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

package com.google.wireless.qa.mobileharness.shared.model.job.out;

import com.google.devtools.common.metrics.stability.model.proto.ErrorIdProto;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionSummary;
import com.google.devtools.common.metrics.stability.model.proto.NamespaceProto.Namespace;
import com.google.devtools.mobileharness.shared.util.error.ErrorModelConverter;
import com.google.wireless.qa.mobileharness.shared.proto.Common.ErrorInfo;

/**
 * Please use {@link Warnings} instead.
 *
 * <p>Warnings of the job/test occur during the execution.
 */
public final class Errors {

  public static ErrorInfo toLegacyErrorInfo(ExceptionProto.ExceptionDetail detail) {
    ExceptionProto.ExceptionSummary summary = detail.getSummary();
    ErrorInfo.Builder errorInfo = ErrorInfo.newBuilder();
    if (summary.getErrorId().getCode() != 0) {
      errorInfo.setCode(summary.getErrorId().getCode());
    }
    if (!summary.getErrorId().getName().isEmpty()) {
      errorInfo.setName(summary.getErrorId().getName());
    }
    if (!summary.getMessage().isEmpty()) {
      errorInfo.setMessage(summary.getMessage());
    }
    return errorInfo
        .setType(summary.getErrorId().getType())
        .setNamespace(summary.getErrorId().getNamespace())
        .setStackTrace(ErrorModelConverter.getCompleteStackTrace(detail))
        .build();
  }

  public static ExceptionProto.ExceptionDetail toExceptionDetail(ErrorInfo legacyErrorInfo) {
    return ExceptionDetail.newBuilder()
        .setSummary(
            ExceptionSummary.newBuilder()
                .setErrorId(
                    ErrorIdProto.ErrorId.newBuilder()
                        .setCode(legacyErrorInfo.getCode())
                        .setName(legacyErrorInfo.getName())
                        .setType(legacyErrorInfo.getType())
                        .setNamespace(
                            legacyErrorInfo.hasNamespace()
                                ? legacyErrorInfo.getNamespace()
                                : Namespace.MH))
                .setMessage(legacyErrorInfo.getMessage()))
        .build();
  }

  private Errors() {}
}
