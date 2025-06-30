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

package com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.rpc.service;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.lab.Annotations.CloudRpcGrpcService;
import com.google.devtools.mobileharness.infra.lab.Annotations.LocalOnlyGrpcService;
import com.google.devtools.mobileharness.infra.lab.common.dir.DirUtil;
import com.google.devtools.mobileharness.infra.lab.controller.FileClassifier;
import com.google.devtools.mobileharness.infra.lab.controller.JobManager;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.TaggedFileHandler;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.proto.TaggedFileMetadataProto.TaggedFileMetadata;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.google.wireless.qa.mobileharness.shared.constant.DirCommon;
import io.grpc.BindableService;
import java.nio.file.Path;
import javax.inject.Singleton;

/** Bindings for CloudFileTransferService. */
public final class CloudFileTransferServiceModule extends AbstractModule {
  @Override
  protected void configure() {
    if (Flags.instance().enableCloudFileTransfer.getNonNull()) {
      Multibinder.newSetBinder(binder(), BindableService.class, LocalOnlyGrpcService.class)
          .addBinding()
          .to(CloudFileTransferServiceGrpcImpl.class);
    }
    Multibinder.newSetBinder(binder(), BindableService.class, CloudRpcGrpcService.class)
        .addBinding()
        .to(CloudFileTransferServiceGrpcImpl.class);
  }

  @Provides
  @Singleton
  CloudFileTransferServiceImpl provideCloudFileTransferServiceImpl()
      throws MobileHarnessException, InterruptedException {
    return new CloudFileTransferServiceImpl(
        Path.of(DirUtil.getCloudReceivedDir()), Path.of(DirCommon.getPublicDirRoot()));
  }

  @Provides
  CloudFileTransferServiceGrpcImpl provideCloudFileTransferServiceGrpcImpl(
      CloudFileTransferServiceImpl cloudFileTransferServiceImpl, JobManager jobManager)
      throws MobileHarnessException {
    return new CloudFileTransferServiceGrpcImpl(cloudFileTransferServiceImpl)
        .addHandler(
            TaggedFileMetadata.class, new TaggedFileHandler(new FileClassifier(jobManager)));
  }
}
