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

package com.google.devtools.deviceinfra.api.error;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.common.metrics.stability.model.proto.NamespaceProto.Namespace;
import com.google.devtools.deviceinfra.api.error.id.DeviceInfraErrorId;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class DeviceInfraExceptionTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private DeviceInfraErrorId errorId;

  @Before
  public void setUp() {
    when(errorId.namespace()).thenReturn(Namespace.MH);
    when(errorId.code()).thenReturn(123);
    when(errorId.name()).thenReturn("FAKE_ERROR_ID");
    when(errorId.type()).thenReturn(ErrorType.INFRA_ISSUE);
  }

  @Test
  public void getMessage() throws Exception {
    assertThat(new DeviceInfraException(errorId, "Fake error message"))
        .hasMessageThat()
        .isEqualTo("Fake error message [MH|INFRA_ISSUE|FAKE_ERROR_ID|123]");

    assertThat(
            new DeviceInfraException(
                errorId, "Fake error message", /* addErrorIdToMessage= */ false))
        .hasMessageThat()
        .isEqualTo("Fake error message");

    assertThat(new DeviceInfraException(errorId, "Fake error message", null))
        .hasMessageThat()
        .isEqualTo("Fake error message [MH|INFRA_ISSUE|FAKE_ERROR_ID|123]");
  }

  @SuppressWarnings("UnnecessaryInitCause")
  @Test
  public void initCause() throws Exception {
    DeviceInfraException exception = new DeviceInfraException(errorId, "Fake error message");

    exception.initCause(new IOException("Fake cause"));

    assertThat(exception).hasCauseThat().hasMessageThat().isEqualTo("Fake cause");
  }
}
