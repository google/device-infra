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
import static java.util.Objects.requireNonNull;

import com.google.common.base.Throwables;
import com.google.common.truth.extensions.proto.ProtoTruth;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.common.metrics.stability.testdata.FakeException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ErrorModelConverterTest {

  @Test
  public void toExceptionDetail() {
    ExceptionDetail exceptionDetail =
        ErrorModelConverter.toExceptionDetail(FakeException.UDCLUSTER_EXCEPTION);

    ProtoTruth.assertThat(exceptionDetail).isEqualTo(FakeException.UDCLUSTER_EXCEPTION_DETAIL);
  }

  @Test
  public void toDeserializedException_toExceptionDetail() {
    ExceptionDetail.Builder expectedExceptionDetail =
        FakeException.UDCLUSTER_EXCEPTION_DETAIL.toBuilder();
    expectedExceptionDetail
        .getSummaryBuilder()
        .setMessage(
            String.format(
                "%s [%s]", expectedExceptionDetail.getSummary().getMessage(), "UdclusterException"))
        .getClassTypeBuilder()
        .setClassName(DeserializedException.class.getName());
    expectedExceptionDetail
        .getCauseBuilder()
        .getSummaryBuilder()
        .setMessage(
            String.format(
                "%s [%s]",
                expectedExceptionDetail.getCause().getSummary().getMessage(),
                "UdclusterUtilException"))
        .getClassTypeBuilder()
        .setClassName(DeserializedException.class.getName());
    expectedExceptionDetail
        .getSuppressedBuilderList()
        .forEach(
            suppressed ->
                suppressed
                    .getSummaryBuilder()
                    .setMessage(
                        String.format(
                            "%s [%s]",
                            suppressed.getSummary().getMessage(), "InterruptedException"))
                    .getClassTypeBuilder()
                    .setClassName(DeserializedException.class.getName()));

    ProtoTruth.assertThat(
            ErrorModelConverter.toExceptionDetail(
                ErrorModelConverter.toDeserializedException(
                    FakeException.UDCLUSTER_EXCEPTION_DETAIL)))
        .isEqualTo(expectedExceptionDetail.build());
  }

  @Test
  public void toDeserializedException() {
    DeserializedException exception =
        ErrorModelConverter.toDeserializedException(FakeException.UDCLUSTER_EXCEPTION_DETAIL);

    assertThat(Throwables.getStackTraceAsString(exception))
        .isEqualTo(FakeException.UDCLUSTER_EXCEPTION_STRING);
  }

  @Test
  public void toExceptionDetail_toDeserializedException() {
    DeserializedException exception =
        ErrorModelConverter.toDeserializedException(
            ErrorModelConverter.toExceptionDetail(FakeException.UDCLUSTER_EXCEPTION));

    assertThat(exception.getOriginalExceptionClassName())
        .isEqualTo(FakeException.UDCLUSTER_EXCEPTION.getClass().getName());
    assertThat(requireNonNull(exception.getCause()).getOriginalExceptionClassName())
        .isEqualTo(FakeException.UDCLUSTER_EXCEPTION.getCause().getClass().getName());
    assertThat(
        ((DeserializedException) exception.getSuppressed()[0]).getOriginalExceptionClassName())
        .isEqualTo(FakeException.UDCLUSTER_EXCEPTION.getSuppressed()[0].getClass().getName());

    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "%s [%s]",
                FakeException.UDCLUSTER_EXCEPTION.getMessage(),
                FakeException.UDCLUSTER_EXCEPTION.getClass().getSimpleName()));
    assertThat(exception)
        .hasCauseThat()
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "%s [%s]",
                FakeException.UDCLUSTER_EXCEPTION.getCause().getMessage(),
                FakeException.UDCLUSTER_EXCEPTION.getCause().getClass().getSimpleName()));
    assertThat(exception.getSuppressed()[0])
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "%s [%s]",
                FakeException.UDCLUSTER_EXCEPTION.getSuppressed()[0].getMessage(),
                FakeException.UDCLUSTER_EXCEPTION.getSuppressed()[0].getClass().getSimpleName()));

    assertThat(exception.getStackTrace())
        .asList()
        .containsExactlyElementsIn(FakeException.UDCLUSTER_EXCEPTION.getStackTrace())
        .inOrder();
    assertThat(exception.getCause().getStackTrace())
        .asList()
        .containsExactlyElementsIn(FakeException.UDCLUSTER_EXCEPTION.getCause().getStackTrace())
        .inOrder();
    assertThat(exception.getSuppressed()[0].getStackTrace())
        .asList()
        .containsExactlyElementsIn(
            FakeException.UDCLUSTER_EXCEPTION.getSuppressed()[0].getStackTrace())
        .inOrder();
  }
}
