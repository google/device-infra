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

package com.google.devtools.mobileharness.service.deviceconfig;

import com.google.common.flags.Flag;
import com.google.common.flags.FlagSpec;
import com.google.common.flags.Flags;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.service.DeviceConfigServiceImpl;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.service.grpc.DeviceConfigGrpcImpl;
import com.google.net.grpc.ProdServerBuilder;
import java.io.IOException;

/** The starter of the DeviceConfigServer. */
final class DeviceConfigServerStarter {
  @FlagSpec(name = "port", help = "port to listen on")
  private static final Flag<Integer> port = Flag.value(10000);

  public static void main(String[] args) throws InterruptedException, IOException {
    Flags.parse(args);

    ProdServerBuilder.forPort(port.get())
        .addService(new DeviceConfigGrpcImpl(new DeviceConfigServiceImpl()))
        // It's commonly a good idea to specify your own fixed-size thread pool to avoid context
        // switch thrashing. How many threads is application-specific. gRPC defaults to a singleton
        // executor.
        // .executor(appExecutor)
        .build()
        .start()
        .awaitTermination();
  }

  private DeviceConfigServerStarter() {}
}
