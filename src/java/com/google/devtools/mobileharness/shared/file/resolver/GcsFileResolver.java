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

package com.google.devtools.mobileharness.shared.file.resolver;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.deviceinfra.shared.util.file.remote.constant.RemoteFileType;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveResult;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveSource;
import com.google.devtools.mobileharness.shared.util.file.remote.GcsUtil;
import com.google.devtools.mobileharness.shared.util.file.remote.GcsUtil.GcsParams;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** File resolver for resolving GCS file. */
public class GcsFileResolver extends AbstractFileResolver {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Pattern GCS_PATH_PATTERN =
      Pattern.compile("^gs://(?<bucket>[^/]+)/(?<path>[^@]+)(@(?<project>.+))?$");

  public GcsFileResolver(@Nullable ListeningExecutorService executorService) {
    super(executorService);
  }

  @Override
  protected boolean shouldActuallyResolve(ResolveSource resolveSource) {
    return resolveSource.path().startsWith(RemoteFileType.GCS.prefix());
  }

  @Override
  protected ResolveResult actuallyResolve(ResolveSource resolveSource)
      throws MobileHarnessException, InterruptedException {
    String fileOrDirPath = resolveSource.path();
    Matcher matcher = GCS_PATH_PATTERN.matcher(fileOrDirPath);
    if (!matcher.matches()) {
      throw new MobileHarnessException(
          BasicErrorId.GCS_ILLEGAL_PATH_ERROR,
          String.format("Illegal GCS file path: %s", fileOrDirPath));
    }

    String bucket = matcher.group("bucket");
    String filePath = matcher.group("path");
    String project = matcher.group("project");
    String gcsPath = String.format("gs://%s/%s", bucket, filePath);
    if (project == null) {
      throw new MobileHarnessException(
          BasicErrorId.GCS_ILLEGAL_PATH_ERROR,
          String.format("No project specified in GCS file path: %s", fileOrDirPath));
    }
    String localPath = PathUtil.join(resolveSource.targetDir(), bucket, filePath);
    String credentialFile = getCredentialFile(project);
    GcsUtil gcsUtil = createGcsUtil(project, bucket, credentialFile);

    logger.atInfo().log("Downloading GCS file %s to %s", fileOrDirPath, localPath);
    gcsUtil.copyFileOrDirectoryToLocal(gcsPath, Path.of(localPath));

    return ResolveResult.of(
        ImmutableList.of(ResolvedFile.create(localPath, null)), ImmutableMap.of(), resolveSource);
  }

  @Nullable
  private static String getCredentialFile(String project) {
    return null;
  }

  @VisibleForTesting
  GcsUtil createGcsUtil(String project, String bucket, @Nullable String credentialFile)
      throws MobileHarnessException {
    return new GcsUtil(
        credentialFile != null
            ? new GcsParams(
                project,
                bucket,
                GcsParams.Scope.READ_ONLY,
                GcsUtil.CredentialType.ofCredentialFile(credentialFile))
            : new GcsParams(
                project, bucket, GcsParams.Scope.READ_ONLY, GcsUtil.CredentialType.ofAppDefault()));
  }
}
