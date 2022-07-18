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
import com.google.devtools.common.metrics.stability.model.proto.ErrorIdProto.ErrorId;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionClassType;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionSummary;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.StackTrace;
import com.google.devtools.common.metrics.stability.model.proto.NamespaceProto.Namespace;
import com.google.devtools.deviceinfra.api.error.DeviceInfraException;
import com.google.devtools.deviceinfra.api.error.id.defined.BasicErrorId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ErrorModelConverterTest {

  private static final DeviceInfraException DEVICE_INFRA_EXCEPTION =
      new DeviceInfraException(
          BasicErrorId.REFLECTION_LOAD_CLASS_ERROR,
          "Failed to load class",
          new DeviceInfraException(
              BasicErrorId.REFLECTION_LOAD_CLASS_TYPE_MISMATCH, "Message of cause", null));

  static {
    DEVICE_INFRA_EXCEPTION.addSuppressed(new InterruptedException("Message of suppressed"));
    DEVICE_INFRA_EXCEPTION.setStackTrace(
        new StackTraceElement[] {
          new StackTraceElement("foo.goo.Class1", "method1", "Class1.java", 123)
        });
    DEVICE_INFRA_EXCEPTION
        .getCause()
        .setStackTrace(
            new StackTraceElement[] {
              new StackTraceElement("foo.goo.Class2", "method2", "Class2.java", 456)
            });
    DEVICE_INFRA_EXCEPTION.getSuppressed()[0].setStackTrace(
        new StackTraceElement[] {
          new StackTraceElement("foo.goo.Class3", "method3", "Class3.java", 789)
        });
  }

  public static final ExceptionDetail DEVICE_INFRA_EXCEPTION_DETAIL =
      ExceptionDetail.newBuilder()
          .setSummary(
              ExceptionSummary.newBuilder()
                  .setErrorId(
                      ErrorId.newBuilder()
                          .setCode(4000001)
                          .setName("REFLECTION_LOAD_CLASS_ERROR")
                          .setType(ErrorType.UNDETERMINED)
                          .setNamespace(Namespace.MH))
                  .setMessage(
                      "Failed to load class [MH|UNDETERMINED|REFLECTION_LOAD_CLASS_ERROR|4000001]")
                  .setClassType(
                      ExceptionClassType.newBuilder()
                          .setClassName(
                              "com.google.devtools.deviceinfra.api.error.DeviceInfraException"))
                  .setStackTrace(
                      StackTrace.newBuilder()
                          .addElement(
                              ExceptionProto.StackTraceElement.newBuilder()
                                  .setClassName("foo.goo.Class1")
                                  .setMethodName("method1")
                                  .setFileName("Class1.java")
                                  .setLineNumber(123))))
          .setCause(
              ExceptionDetail.newBuilder()
                  .setSummary(
                      ExceptionSummary.newBuilder()
                          .setErrorId(
                              ErrorId.newBuilder()
                                  .setCode(4000002)
                                  .setName("REFLECTION_LOAD_CLASS_TYPE_MISMATCH")
                                  .setType(ErrorType.UNDETERMINED)
                                  .setNamespace(Namespace.MH))
                          .setMessage(
                              "Message of cause "
                                  + "[MH|UNDETERMINED|REFLECTION_LOAD_CLASS_TYPE_MISMATCH|4000002]")
                          .setClassType(
                              ExceptionClassType.newBuilder()
                                  .setClassName(
                                      "com.google.devtools.deviceinfra.api.error."
                                          + "DeviceInfraException"))
                          .setStackTrace(
                              StackTrace.newBuilder()
                                  .addElement(
                                      ExceptionProto.StackTraceElement.newBuilder()
                                          .setClassName("foo.goo.Class2")
                                          .setMethodName("method2")
                                          .setFileName("Class2.java")
                                          .setLineNumber(456)))))
          .addSuppressed(
              ExceptionDetail.newBuilder()
                  .setSummary(
                      ExceptionSummary.newBuilder()
                          .setErrorId(
                              ErrorId.newBuilder()
                                  .setCode(UnknownErrorId.NOT_DEFINED.code())
                                  .setName(UnknownErrorId.NOT_DEFINED.name())
                                  .setType(UnknownErrorId.NOT_DEFINED.type())
                                  .setNamespace(UnknownErrorId.NOT_DEFINED.namespace()))
                          .setMessage("Message of suppressed")
                          .setClassType(
                              ExceptionClassType.newBuilder()
                                  .setClassName("java.lang.InterruptedException"))
                          .setStackTrace(
                              StackTrace.newBuilder()
                                  .addElement(
                                      ExceptionProto.StackTraceElement.newBuilder()
                                          .setClassName("foo.goo.Class3")
                                          .setMethodName("method3")
                                          .setFileName("Class3.java")
                                          .setLineNumber(789)))))
          .build();

  public static final String DEVICE_INFRA_EXCEPTION_STRING =
      "com.google.devtools.common.metrics.stability.converter.DeserializedException: Failed to load"
          + " class [MH|UNDETERMINED|REFLECTION_LOAD_CLASS_ERROR|4000001] [DeviceInfraException]\n"
          + "\tat foo.goo.Class1.method1(Class1.java:123)\n"
          + "\tSuppressed:"
          + " com.google.devtools.common.metrics.stability.converter.DeserializedException: Message"
          + " of suppressed [InterruptedException]\n"
          + "\t\tat foo.goo.Class3.method3(Class3.java:789)\n"
          + "Caused by:"
          + " com.google.devtools.common.metrics.stability.converter.DeserializedException: Message"
          + " of cause [MH|UNDETERMINED|REFLECTION_LOAD_CLASS_TYPE_MISMATCH|4000002]"
          + " [DeviceInfraException]\n"
          + "\tat foo.goo.Class2.method2(Class2.java:456)\n";

  @Test
  public void toExceptionDetail() {
    ExceptionDetail exceptionDetail = ErrorModelConverter.toExceptionDetail(DEVICE_INFRA_EXCEPTION);

    assertThat(exceptionDetail).isEqualTo(DEVICE_INFRA_EXCEPTION_DETAIL);
  }

  @Test
  public void toDeserializedException_toExceptionDetail() {
    ExceptionDetail.Builder expectedExceptionDetail = DEVICE_INFRA_EXCEPTION_DETAIL.toBuilder();
    expectedExceptionDetail
        .getSummaryBuilder()
        .setMessage(
            String.format(
                "%s [%s]",
                expectedExceptionDetail.getSummary().getMessage(), "DeviceInfraException"))
        .getClassTypeBuilder()
        .setClassName(DeserializedException.class.getName());
    expectedExceptionDetail
        .getCauseBuilder()
        .getSummaryBuilder()
        .setMessage(
            String.format(
                "%s [%s]",
                expectedExceptionDetail.getCause().getSummary().getMessage(),
                "DeviceInfraException"))
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

    assertThat(
            ErrorModelConverter.toExceptionDetail(
                ErrorModelConverter.toDeserializedException(DEVICE_INFRA_EXCEPTION_DETAIL)))
        .isEqualTo(expectedExceptionDetail.build());
  }

  @Test
  public void toDeserializedException() {
    DeserializedException exception =
        ErrorModelConverter.toDeserializedException(DEVICE_INFRA_EXCEPTION_DETAIL);

    assertThat(Throwables.getStackTraceAsString(exception))
        .isEqualTo(DEVICE_INFRA_EXCEPTION_STRING);
  }

  @Test
  public void toExceptionDetail_toDeserializedException() {
    DeserializedException exception =
        ErrorModelConverter.toDeserializedException(
            ErrorModelConverter.toExceptionDetail(DEVICE_INFRA_EXCEPTION));

    assertThat(exception.getOriginalExceptionClassName())
        .isEqualTo(DEVICE_INFRA_EXCEPTION.getClass().getName());
    assertThat(requireNonNull(exception.getCause()).getOriginalExceptionClassName())
        .isEqualTo(DEVICE_INFRA_EXCEPTION.getCause().getClass().getName());
    assertThat(
            ((DeserializedException) exception.getSuppressed()[0]).getOriginalExceptionClassName())
        .isEqualTo(DEVICE_INFRA_EXCEPTION.getSuppressed()[0].getClass().getName());

    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "%s [%s]",
                DEVICE_INFRA_EXCEPTION.getMessage(),
                DEVICE_INFRA_EXCEPTION.getClass().getSimpleName()));
    assertThat(exception)
        .hasCauseThat()
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "%s [%s]",
                DEVICE_INFRA_EXCEPTION.getCause().getMessage(),
                DEVICE_INFRA_EXCEPTION.getCause().getClass().getSimpleName()));
    assertThat(exception.getSuppressed()[0])
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "%s [%s]",
                DEVICE_INFRA_EXCEPTION.getSuppressed()[0].getMessage(),
                DEVICE_INFRA_EXCEPTION.getSuppressed()[0].getClass().getSimpleName()));

    assertThat(exception.getStackTrace())
        .asList()
        .containsExactlyElementsIn(DEVICE_INFRA_EXCEPTION.getStackTrace())
        .inOrder();
    assertThat(exception.getCause().getStackTrace())
        .asList()
        .containsExactlyElementsIn(DEVICE_INFRA_EXCEPTION.getCause().getStackTrace())
        .inOrder();
    assertThat(exception.getSuppressed()[0].getStackTrace())
        .asList()
        .containsExactlyElementsIn(DEVICE_INFRA_EXCEPTION.getSuppressed()[0].getStackTrace())
        .inOrder();
  }
}
