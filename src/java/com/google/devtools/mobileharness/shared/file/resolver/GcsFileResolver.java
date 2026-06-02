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

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.deviceinfra.shared.util.file.remote.constant.RemoteFileType;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveResult;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveSource;
import com.google.devtools.mobileharness.shared.util.file.checksum.proto.ChecksumProto.Algorithm;
import com.google.devtools.mobileharness.shared.util.file.checksum.proto.ChecksumProto.Checksum;
import com.google.devtools.mobileharness.shared.util.file.remote.GcsUtil;
import com.google.devtools.mobileharness.shared.util.file.remote.GcsUtil.GcsParams;
import com.google.devtools.mobileharness.shared.util.file.remote.GcsUtilFactory;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** File resolver for resolving GCS file. */
public class GcsFileResolver extends AbstractFileResolver {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String PARAM_GCS_SERVICE_ACCOUNT = "gcs_service_account";

  private static final Pattern GCS_PATH_PATTERN =
      Pattern.compile("^gs://(?<bucket>[^/]+)/(?<path>[^@]+)(@(?<project>.+))?$");

  private final LoadingCache<GcsKey, GcsUtil> gcsUtilCache;

  @AutoValue
  abstract static class GcsKey {
    abstract String project();

    abstract String bucket();

    abstract Optional<String> credentialFile();

    abstract Optional<String> serviceAccount();

    static GcsKey create(
        String project,
        String bucket,
        Optional<String> credentialFile,
        Optional<String> serviceAccount) {
      return new AutoValue_GcsFileResolver_GcsKey(project, bucket, credentialFile, serviceAccount);
    }
  }

  @AutoValue
  abstract static class ParseResult {
    abstract GcsKey gcsKey();

    // The object path in GCS relative to the bucket.
    abstract String objectPath();

    static ParseResult create(GcsKey gcsKey, String objectPath) {
      return new AutoValue_GcsFileResolver_ParseResult(gcsKey, objectPath);
    }
  }

  @Inject
  public GcsFileResolver(@Nullable ListeningExecutorService executorService) {
    super(executorService);
    CacheLoader<GcsKey, GcsUtil> cacheLoader =
        new CacheLoader<>() {
          @Override
          public GcsUtil load(GcsKey key) throws MobileHarnessException {
            return createGcsUtil(
                key.project(),
                key.bucket(),
                key.credentialFile().orElse(null),
                key.serviceAccount().orElse(null));
          }
        };
    gcsUtilCache = CacheBuilder.newBuilder().build(cacheLoader);
  }

  @Override
  protected boolean shouldActuallyResolve(ResolveSource resolveSource) {
    return resolveSource.path().startsWith(RemoteFileType.GCS.prefix());
  }

  @Override
  protected ResolveResult actuallyResolve(ResolveSource resolveSource)
      throws MobileHarnessException, InterruptedException {
    ParseResult parseResult = parseGcsPath(resolveSource);
    String bucket = parseResult.gcsKey().bucket();
    String filePath = parseResult.objectPath();
    String localPath = PathUtil.join(resolveSource.targetDir(), bucket, filePath);
    String gcsPath = String.format("gs://%s/%s", bucket, filePath);
    GcsUtil gcsUtil = getGcsUtil(parseResult.gcsKey());

    logger.atInfo().log("Downloading GCS file %s to %s", gcsPath, localPath);
    gcsUtil.copyFileOrDirectoryToLocal(gcsPath, Path.of(localPath));
    Optional<String> checksum = gcsUtil.getCrc32c(Path.of(filePath));
    return ResolveResult.of(
        ImmutableList.of(
            ResolvedFile.create(localPath, checksum.orElse(null), resolveSource.path())),
        ImmutableMap.of(),
        resolveSource);
  }

  @Override
  protected Optional<Checksum> computeChecksum(ResolveSource resolveSource)
      throws MobileHarnessException, InterruptedException {
    ParseResult parseResult = parseGcsPath(resolveSource);
    GcsUtil gcsUtil = getGcsUtil(parseResult.gcsKey());
    return gcsUtil
        .getCrc32cBytes(Path.of(parseResult.objectPath()))
        .map(
            crc32c ->
                Checksum.newBuilder().setAlgorithm(Algorithm.GCS_CRC32C).setData(crc32c).build());
  }

  private static ParseResult parseGcsPath(ResolveSource resolveSource)
      throws MobileHarnessException {
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
    if (project == null && Flags.gcsResolverProjectId.get() != null) {
      project = Flags.gcsResolverProjectId.get();
    }
    if (project == null) {
      throw new MobileHarnessException(
          BasicErrorId.GCS_ILLEGAL_PATH_ERROR,
          String.format("No project specified in GCS file path: %s", fileOrDirPath));
    }
    String credentialFile = getCredentialFile(project);
    String serviceAccount = resolveSource.parameters().get(PARAM_GCS_SERVICE_ACCOUNT);

    return ParseResult.create(
        GcsKey.create(
            project,
            bucket,
            Optional.ofNullable(credentialFile),
            Optional.ofNullable(serviceAccount)),
        filePath);
  }

  @Nullable
  private static String getCredentialFile(String project) {
    if (Flags.gcsResolverCredentialFile.get() != null) {
      return Flags.gcsResolverCredentialFile.get();
    }
    return null;
  }

  private GcsUtil getGcsUtil(GcsKey gcsKey) throws MobileHarnessException {
    try {
      return gcsUtilCache.get(gcsKey);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof MobileHarnessException) {
        throw (MobileHarnessException) e.getCause();
      } else {
        throw new AssertionError("Should not happen.", e);
      }
    }
  }

  @VisibleForTesting
  GcsUtil createGcsUtil(
      String project,
      String bucket,
      @Nullable String credentialFile,
      @Nullable String serviceAccount)
      throws MobileHarnessException {
    GcsUtil.CredentialType credentialType = GcsUtil.CredentialType.ofAppDefault();
    if (credentialFile != null) {
      credentialType = GcsUtil.CredentialType.ofCredentialFile(credentialFile);
    }

    return GcsUtilFactory.create(
        new GcsParams(project, bucket, GcsParams.Scope.READ_ONLY, credentialType));
  }
}
