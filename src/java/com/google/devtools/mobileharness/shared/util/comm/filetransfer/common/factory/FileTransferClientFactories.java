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

package com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.factory;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.client.CloudFileTransferClient;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.rpc.stub.CloudFileTransferStubInterface;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.comm.filetransfer.FileTransferClient;

/**
 * Factories of {@link FileTransferClient}. It creates different factories according to the
 * differences of parameters.
 */
public final class FileTransferClientFactories {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Creates a cloud file transfer client factory from a stub. */
  public static FileTransferClientFactory fromStub(CloudFileTransferStubInterface stub) {
    return new CloudFileTransferStubFactory(stub);
  }

  static class CloudFileTransferStubFactory implements FileTransferClientFactory {
    private final CloudFileTransferStubInterface stub;

    CloudFileTransferStubFactory(CloudFileTransferStubInterface stub) {
      this.stub = stub;
    }

    @CanIgnoreReturnValue
    @Override
    public FileTransferClient create(FileTransferParameters parameters)
        throws MobileHarnessException, InterruptedException {
      return new CloudFileTransferClient(stub, parameters);
    }

    @Override
    public void shutdown() {
      stub.shutdown();
    }
  }

  private FileTransferClientFactories() {}
}
