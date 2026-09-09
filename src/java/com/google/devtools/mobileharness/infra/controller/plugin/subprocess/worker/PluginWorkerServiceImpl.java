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

/*
 * Copyright 2026 Google LLC
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

package com.google.devtools.mobileharness.infra.controller.plugin.subprocess.worker;

import com.google.devtools.mobileharness.infra.controller.plugin.proto.PluginWorkerServiceGrpc;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.RunTestEventRequest;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.RunTestEventResponse;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.ShutdownRequest;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.ShutdownResponse;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.atomic.AtomicBoolean;

/** gRPC service implementation running inside the isolated plugin worker subprocess. */
public final class PluginWorkerServiceImpl
    extends PluginWorkerServiceGrpc.PluginWorkerServiceImplBase {

  private final WorkerEventDispatcher dispatcher;
  private final Runnable onShutdown;
  private final AtomicBoolean isShutdown = new AtomicBoolean(false);

  public PluginWorkerServiceImpl(WorkerEventDispatcher dispatcher, Runnable onShutdown) {
    this.dispatcher = dispatcher;
    this.onShutdown = onShutdown;
  }

  @Override
  public void runTestEvent(
      RunTestEventRequest request, StreamObserver<RunTestEventResponse> responseObserver) {
    try {
      RunTestEventResponse response = dispatcher.dispatchEvent(request);
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (Throwable t) {
      responseObserver.onError(t);
    }
  }

  @Override
  public void shutdown(ShutdownRequest request, StreamObserver<ShutdownResponse> responseObserver) {
    responseObserver.onNext(ShutdownResponse.newBuilder().setSuccess(true).build());
    responseObserver.onCompleted();

    if (isShutdown.compareAndSet(false, true)) {
      new Thread(onShutdown, "worker-shutdown-thread").start();
    }
  }
}
