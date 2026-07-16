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

package com.google.devtools.mobileharness.fe.v6.service.errors;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import io.grpc.Status;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class FeServiceExceptionTest {

  @Test
  public void constructor_setsCodeAndMessage() {
    FeServiceException e = new FeServiceException(Status.Code.NOT_FOUND, "host missing");

    assertThat(e.getCode()).isEqualTo(Status.Code.NOT_FOUND);
    assertThat(e).hasMessageThat().isEqualTo("host missing");
    assertThat(e).hasCauseThat().isNull();
  }

  @Test
  public void constructor_withCause_setsCause() {
    Throwable cause = new RuntimeException("root");

    FeServiceException e = new FeServiceException(Status.Code.INTERNAL, "boom", cause);

    assertThat(e.getCode()).isEqualTo(Status.Code.INTERNAL);
    assertThat(e).hasCauseThat().isSameInstanceAs(cause);
  }

  @Test
  public void constructor_nullCode_throws() {
    assertThrows(NullPointerException.class, () -> new FeServiceException(null, "msg"));
  }

  @Test
  public void factoryMethods_setExpectedCodes() {
    assertThat(FeServiceException.invalidArgument("m").getCode())
        .isEqualTo(Status.Code.INVALID_ARGUMENT);
    assertThat(FeServiceException.notFound("m").getCode()).isEqualTo(Status.Code.NOT_FOUND);
    assertThat(FeServiceException.permissionDenied("m").getCode())
        .isEqualTo(Status.Code.PERMISSION_DENIED);
    assertThat(FeServiceException.failedPrecondition("m").getCode())
        .isEqualTo(Status.Code.FAILED_PRECONDITION);
    assertThat(FeServiceException.unimplemented("m").getCode())
        .isEqualTo(Status.Code.UNIMPLEMENTED);
    assertThat(FeServiceException.internal("m").getCode()).isEqualTo(Status.Code.INTERNAL);
  }

  @Test
  public void factoryMethods_setMessage() {
    assertThat(FeServiceException.notFound("host missing"))
        .hasMessageThat()
        .isEqualTo("host missing");
  }
}
