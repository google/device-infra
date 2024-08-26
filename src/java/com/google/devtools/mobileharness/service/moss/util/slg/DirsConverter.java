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

import com.google.devtools.mobileharness.api.model.job.in.Dirs;
import com.google.devtools.mobileharness.service.moss.proto.Slg.DirsProto;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;

/** Utility class to convert a {@link Dirs} to a {@link DirsProto} in forward and backward. */
final class DirsConverter {

  private DirsConverter() {}

  /**
   * Gets a {@link Dirs} by the given {@link DirsProto} and the prefix to help create local
   * directories.
   *
   * @param jobDir the job root directory
   */
  static Dirs fromProto(String jobDir, DirsProto dirsProto) {
    String genFileDir = PathUtil.join(jobDir, "gen");
    String tmpFileDir = PathUtil.join(jobDir, "tmp");
    String runFileDir = PathUtil.join(jobDir, "run");
    return new Dirs(
        genFileDir,
        tmpFileDir,
        runFileDir,
        dirsProto.getRemoteFileDirPath().isEmpty() ? null : dirsProto.getRemoteFileDirPath(),
        dirsProto.getHasTestSubDirs(),
        new LocalFileUtil());
  }

  /**
   * Gets a {@link DirsProto} by the given {@link Dirs}. Only meaningful fields will be persistent.
   */
  static DirsProto toProto(Dirs dirs) {
    DirsProto.Builder dirsProto = DirsProto.newBuilder().setHasTestSubDirs(dirs.hasTestSubdirs());
    dirs.remoteFileDir().ifPresent(dirsProto::setRemoteFileDirPath);
    return dirsProto.build();
  }
}
