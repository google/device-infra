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

package com.google.devtools.mobileharness.service.moss.util.slg;

import com.google.devtools.mobileharness.service.moss.proto.Slg.RemoteFilesProto;
import com.google.wireless.qa.mobileharness.shared.model.job.out.JobOutInternalFactory;
import com.google.wireless.qa.mobileharness.shared.model.job.out.RemoteFiles;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;

/**
 * Utility class to help convert a {@link RemoteFiles} to a {@link RemoteFilesProto} in forward or
 * backward.
 */
final class RemoteFilesConverter {

  private RemoteFilesConverter() {}

  /** Gets a {@link RemoteFiles} by the given {@link Timing} and {@link RemoteFilesProto}. */
  static RemoteFiles fromProto(Timing timing, RemoteFilesProto remoteFilesProto) {
    return JobOutInternalFactory.createRemoteFiles(timing, remoteFilesProto);
  }

  /** Gets a {@link RemoteFilesProto} by the given {@link RemoteFiles}. */
  static RemoteFilesProto toProto(RemoteFiles remoteFiles) {
    RemoteFilesProto.Builder remoteFilesProto = RemoteFilesProto.newBuilder();
    remoteFiles.getRootPath().ifPresent(remoteFilesProto::setRootPath);
    remoteFilesProto.addAllRemoteFilePath(remoteFiles.getAll());
    return remoteFilesProto.build();
  }
}
