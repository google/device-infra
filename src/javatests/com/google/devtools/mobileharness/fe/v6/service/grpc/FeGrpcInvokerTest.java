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

package com.google.devtools.mobileharness.fe.v6.service.grpc;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.util.concurrent.Futures;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostOverviewRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostOverviewPageData;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class FeGrpcInvokerTest {

  /** Minimal StreamObserver that records interactions. */
  private static final class RecordingObserver<T> implements StreamObserver<T> {
    final List<T> values = new ArrayList<>();
    Throwable error;
    boolean completed;

    @Override
    public void onNext(T value) {
      values.add(value);
    }

    @Override
    public void onError(Throwable t) {
      error = t;
    }

    @Override
    public void onCompleted() {
      completed = true;
    }
  }

  private final RecordingObserver<HostOverviewPageData> observer = new RecordingObserver<>();

  @Test
  public void invokeAsync_success_sendsResponseAndCompletes() {
    HostOverviewPageData response = HostOverviewPageData.getDefaultInstance();

    FeGrpcInvoker.invokeAsync(
        GetHostOverviewRequest.getDefaultInstance(),
        observer,
        req -> Futures.immediateFuture(response),
        directExecutor(),
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getGetHostOverviewMethod());

    assertThat(observer.values).containsExactly(response);
    assertThat(observer.completed).isTrue();
    assertThat(observer.error).isNull();
  }

  @Test
  public void invokeAsync_feExceptionFromFuture_mapsToCanonicalStatus() {
    FeGrpcInvoker.invokeAsync(
        GetHostOverviewRequest.getDefaultInstance(),
        observer,
        req -> Futures.immediateFailedFuture(FeServiceException.notFound("host x missing")),
        directExecutor(),
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getGetHostOverviewMethod());

    assertThat(observer.error).isInstanceOf(StatusRuntimeException.class);
    Status status = ((StatusRuntimeException) observer.error).getStatus();
    assertThat(status.getCode()).isEqualTo(Status.Code.NOT_FOUND);
    assertThat(status.getDescription()).isEqualTo("host x missing");
  }

  @Test
  public void invokeAsync_feExceptionThrownSynchronously_mapsToCanonicalStatus() {
    FeGrpcInvoker.invokeAsync(
        GetHostOverviewRequest.getDefaultInstance(),
        observer,
        req -> {
          throw FeServiceException.invalidArgument("bad input");
        },
        directExecutor(),
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getGetHostOverviewMethod());

    assertThat(observer.error).isInstanceOf(StatusRuntimeException.class);
    assertThat(((StatusRuntimeException) observer.error).getStatus().getCode())
        .isEqualTo(Status.Code.INVALID_ARGUMENT);
  }

  @Test
  public void invokeAsync_wrappedFeException_mapsToCanonicalStatus() {
    FeGrpcInvoker.invokeAsync(
        GetHostOverviewRequest.getDefaultInstance(),
        observer,
        req ->
            Futures.immediateFailedFuture(
                new RuntimeException("wrapper", FeServiceException.permissionDenied("nope"))),
        directExecutor(),
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getGetHostOverviewMethod());

    assertThat(observer.error).isInstanceOf(StatusRuntimeException.class);
    assertThat(((StatusRuntimeException) observer.error).getStatus().getCode())
        .isEqualTo(Status.Code.PERMISSION_DENIED);
  }

  @Test
  public void invokeAsync_genericException_mapsToInternal() {
    FeGrpcInvoker.invokeAsync(
        GetHostOverviewRequest.getDefaultInstance(),
        observer,
        req -> Futures.immediateFailedFuture(new RuntimeException("boom")),
        directExecutor(),
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getGetHostOverviewMethod());

    assertThat(observer.error).isInstanceOf(StatusRuntimeException.class);
    assertThat(((StatusRuntimeException) observer.error).getStatus().getCode())
        .isEqualTo(Status.Code.INTERNAL);
  }
}
