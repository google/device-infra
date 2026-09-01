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

package com.google.devtools.mobileharness.fe.v6.service.device.handlers;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TakeScreenshotRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TakeScreenshotResponse;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import io.grpc.Status;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class TakeScreenshotHandlerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private ScreenshotActionHelper screenshotActionHelper;

  @Inject private TakeScreenshotHandler takeScreenshotHandler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void takeScreenshot_success() throws Exception {
    TakeScreenshotRequest request = TakeScreenshotRequest.newBuilder().setId("device_id").build();
    TakeScreenshotResponse expectedResponse =
        TakeScreenshotResponse.newBuilder().setScreenshotUrl("http://gcs/path").build();

    when(screenshotActionHelper.takeScreenshot(request, UniverseScope.SELF))
        .thenReturn(immediateFuture(expectedResponse));

    TakeScreenshotResponse response =
        takeScreenshotHandler
            .takeScreenshot(request, UniverseScope.SELF, Optional.of("user"))
            .get();

    assertThat(response).isEqualTo(expectedResponse);
    verify(screenshotActionHelper).takeScreenshot(request, UniverseScope.SELF);
  }

  @Test
  public void takeScreenshot_helperError_propagates() {
    TakeScreenshotRequest request = TakeScreenshotRequest.newBuilder().setId("device_id").build();

    when(screenshotActionHelper.takeScreenshot(request, UniverseScope.SELF))
        .thenReturn(
            immediateFailedFuture(FeServiceException.permissionDenied("Permission denied")));

    ExecutionException thrown =
        assertThrows(
            ExecutionException.class,
            () ->
                takeScreenshotHandler
                    .takeScreenshot(request, UniverseScope.SELF, Optional.of("user"))
                    .get());

    assertThat(thrown).hasCauseThat().isInstanceOf(FeServiceException.class);
    FeServiceException cause = (FeServiceException) thrown.getCause();
    assertThat(cause.getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
  }
}
