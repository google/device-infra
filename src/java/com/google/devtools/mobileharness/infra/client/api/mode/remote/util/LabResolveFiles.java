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

package com.google.devtools.mobileharness.infra.client.api.mode.remote.util;

import com.google.common.collect.ImmutableList;
import com.google.devtools.deviceinfra.shared.util.file.remote.constant.RemoteFileType;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CreateTestRequest.ResolveFileItem;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import javax.annotation.Nullable;

/**
 * Static utilities for the job files of a remote test, which the client either sends to the lab or
 * lists in {@code CreateTestRequest} for the lab to resolve itself.
 */
public final class LabResolveFiles {

  private static final LocalFileUtil localFileUtil = new LocalFileUtil();

  /**
   * Returns the relative path under the lab's run-file directory for a client-transferred file with
   * the given resolved {@code path} and original {@code fileSpec}.
   */
  public static String getRelativePath(String path, @Nullable String fileSpec) {
    return fileSpec == null ? path : getRelativePath(fileSpec);
  }

  /**
   * Returns the relative path under the lab's run-file directory for a client-transferred file with
   * the given {@code fileSpec}.
   *
   * <p>A remote spec is split into directory segments at {@code ::} (and at the last {@code file=}
   * of an Android Build spec) and escaped so that the lab copy ends in the artifact's filename
   * without raw spec syntax in its path.
   */
  public static String getRelativePath(String fileSpec) {
    if (localFileUtil.isLocalFileOrDir(fileSpec)) {
      return fileSpec;
    }
    String result = fileSpec;
    return localFileUtil.escapeFilePath(result.replace("::", "/"));
  }

  /**
   * Returns whether the lab fetches {@code filePath} itself rather than receiving it.
   *
   * <p>{@code filePath} is the path the resolvers of the client left, which keep such a file as the
   * job gave it unless the job has them fetch it, e.g. with {@code
   * resolve_ats_file_server_files_in_lab=false}.
   */
  public static boolean isResolvedInLab(String filePath, Params jobParams) {
    return filePath.startsWith(RemoteFileType.ATS_FILE_SERVER.prefix())
        || cachePersistentFileInLab(filePath, jobParams);
  }

  /** Returns the item the lab resolves {@code filePath} of {@code tag} from. */
  public static ResolveFileItem createResolveFileItem(
      String tag, String filePath, JobInfo jobInfo) {
    ResolveFileItem.Builder builder = ResolveFileItem.newBuilder().setTag(tag).setFile(filePath);
    if (cachePersistentFileInLab(filePath, jobInfo.params())) {
      builder.putResolvingParameter("persistent", "true");
    }
    return builder.build();
  }

  private static boolean cachePersistentFileInLab(String filePath, Params jobParams) {
    return Flags.enablePersistentCache.getNonNull()
        && jobParams
            .getList(
                JobInfo.PARAM_PERSISTENT_CACHE_FILE_LIST,
                JobInfo.PERSISTENT_CACHE_FILE_LIST_SEPARATOR,
                ImmutableList.of())
            .contains(filePath);
  }

  private LabResolveFiles() {}
}
