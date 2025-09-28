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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.net.UrlEscapers;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.deviceinfra.shared.util.file.remote.constant.RemoteFileType;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandFailureException;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.file.checksum.proto.ChecksumProto.Algorithm;
import com.google.devtools.mobileharness.shared.util.file.checksum.proto.ChecksumProto.Checksum;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.ByteString;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** The resolver for files in ats file server. */
public class AtsFileServerFileResolver extends AbstractFileResolver {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Pattern LIMIT_RATE_PATTERN = Pattern.compile("^\\d+[KMG]$");

  @ParamAnnotation(
      required = false,
      help =
          "whether or not to resolve ATS file server files in lab server. By default, it's resolved"
              + " in client.")
  public static final String PARAM_RESOLVE_ATS_FILE_SERVER_FILES_IN_LAB =
      "resolve_ats_file_server_files_in_lab";

  static final int DOWNLOAD_RETRY_INTERVAL_SEC = 30;
  private static final int MAX_DOWNLOAD_ATTEMPTS = 3;

  private final LocalFileUtil localFileUtil;
  private final Sleeper sleeper;

  public AtsFileServerFileResolver(
      @Nullable ListeningExecutorService executorService, LocalFileUtil localFileUtil) {
    this(executorService, localFileUtil, Sleeper.defaultSleeper());
  }

  @VisibleForTesting
  AtsFileServerFileResolver(
      @Nullable ListeningExecutorService executorService,
      LocalFileUtil localFileUtil,
      Sleeper sleeper) {
    super(executorService);
    this.localFileUtil = localFileUtil;
    this.sleeper = sleeper;
  }

  @Override
  protected boolean shouldActuallyResolve(ResolveSource resolveSource) {
    return resolveSource.path().startsWith(RemoteFileType.ATS_FILE_SERVER.prefix());
  }

  @Override
  protected ResolveResult actuallyResolve(ResolveSource resolveSource)
      throws InterruptedException, MobileHarnessException {
    String path = resolveSource.path();
    if (Boolean.parseBoolean(
        resolveSource.parameters().get(PARAM_RESOLVE_ATS_FILE_SERVER_FILES_IN_LAB))) {
      return ResolveResult.of(
          ImmutableList.of(ResolvedFile.create(path, null)), ImmutableMap.of(), resolveSource);
    } else {
      String sourcePath = getSourcePath(path);
      String httpSourcePath =
          String.join(
              "/",
              Flags.instance().atsFileServer.getNonNull(),
              PathUtil.join("file", UrlEscapers.urlFragmentEscaper().escape(sourcePath)));
      try {
        httpSourcePath = new URI(httpSourcePath).normalize().toString();
      } catch (URISyntaxException e) {
        throw new MobileHarnessException(
            BasicErrorId.HTTP_INVALID_FILE_PATH_ERROR,
            String.format("Invalid file path %s in ats file server.", httpSourcePath),
            e);
      }
      String destination = PathUtil.join(resolveSource.targetDir(), sourcePath);
      localFileUtil.prepareParentDir(destination);
      for (int i = 0; i < MAX_DOWNLOAD_ATTEMPTS; i++) {
        try {
          downloadFile(destination, httpSourcePath);
          break;
        } catch (MobileHarnessException e) {
          if (i < MAX_DOWNLOAD_ATTEMPTS - 1) {
            logger.atWarning().log(
                "Failed to download file %s from ats file server. Retrying...", httpSourcePath);
            sleeper.sleep(Duration.ofSeconds(DOWNLOAD_RETRY_INTERVAL_SEC));
            continue;
          }
          throw e;
        }
      }
      return ResolveResult.of(
          ImmutableList.of(ResolvedFile.create(destination, null)),
          ImmutableMap.of(),
          resolveSource);
    }
  }

  @Override
  protected Optional<Checksum> computeChecksum(ResolveSource resolveSource)
      throws MobileHarnessException, InterruptedException {
    return getFileSha256(getSourcePath(resolveSource.path()))
        .map(
            sha256 ->
                Checksum.newBuilder()
                    .setAlgorithm(Algorithm.ATS_FILE_SERVER_SHA256)
                    .setData(ByteString.copyFrom(Base64.getDecoder().decode(sha256)))
                    .build());
  }

  private Optional<String> getFileSha256(String sourcePath) throws InterruptedException {
    String httpHashPath =
        String.join(
            "/",
            Flags.instance().atsFileServer.getNonNull(),
            PathUtil.join("hash", UrlEscapers.urlFragmentEscaper().escape(sourcePath)));
    try {
      httpHashPath = new URI(httpHashPath).normalize().toString();
    } catch (URISyntaxException e) {
      logger.atWarning().withCause(e).log("Invalid hash path %s in ats file server.", httpHashPath);
      return Optional.empty();
    }
    try {
      String output =
          createCommandExecutor()
              .run(Command.of("curl", "-sfL", httpHashPath).timeout(Duration.ofMinutes(1)));
      logger.atInfo().log(
          "Received response from ATS file server /hash endpoint for %s: %s", sourcePath, output);
      JsonObject jsonObject = JsonParser.parseString(output).getAsJsonObject();
      if (jsonObject.has("sha256")) {
        return Optional.of(jsonObject.get("sha256").getAsString());
      } else {
        logger.atWarning().log(
            "Failed to get sha256 for %s from ats file server, response: %s", sourcePath, output);
        return Optional.empty();
      }
    } catch (MobileHarnessException e) {
      if (e instanceof CommandFailureException commandFailureException) {
        CommandResult result = commandFailureException.result();
        logger.atWarning().log(
            "Logs of failed ats file hasher: STDOUT: %s\nSTDERR: %s",
            result.stdout(), result.stderr());
      }
      logger.atWarning().withCause(e).log(
          "Failed to get sha256 for %s from ats file server.", sourcePath);
      return Optional.empty();
    }
  }

  private static String getSourcePath(String path) {
    return path.replace(RemoteFileType.ATS_FILE_SERVER.prefix(), "");
  }

  private void downloadFile(String destination, String httpSourcePath)
      throws InterruptedException, MobileHarnessException {
    try {
      String output =
          createCommandExecutor()
              .run(
                  Command.of(createCurlCommand(destination, httpSourcePath))
                      .timeout(Duration.ofHours(2)));
      logger.atInfo().log(
          "Output of ats file server downloader for downloading file %s: %s",
          httpSourcePath, output);
      return;
    } catch (CommandException e) {
      if (e instanceof CommandFailureException) {
        CommandResult result = ((CommandFailureException) e).result();
        logger.atWarning().log(
            "Logs of failed ats file downloader: STDOUT: %s\nSTDERR: %s",
            result.stdout(), result.stderr());
      }
      throw new MobileHarnessException(
          BasicErrorId.ATS_FILE_SERVER_RESOLVE_FILE_ERROR,
          String.format("Failed to download file %s from ats file server.", httpSourcePath),
          e);
    }
  }

  @VisibleForTesting
  CommandExecutor createCommandExecutor() {
    return new CommandExecutor();
  }

  @VisibleForTesting
  List<String> createCurlCommand(String destination, String httpSourcePath) {
    // Add -C - to support download resume.
    // Add -L to handle redirects.
    List<String> command =
        new ArrayList<>(Arrays.asList("curl", "-C", "-", "-o", destination, "-fL", httpSourcePath));
    if (Flags.instance().tradefedCurlDownloadLimitRate.get() != null) {
      String limitRate = Flags.instance().tradefedCurlDownloadLimitRate.get();
      checkArgument(isLimitRateValue(limitRate), "Invalid curl download rate limit: %s", limitRate);
      command.add("--limit-rate");
      command.add(limitRate);
    }
    return command;
  }

  @VisibleForTesting
  static boolean isLimitRateValue(String value) {
    return LIMIT_RATE_PATTERN.matcher(value).matches();
  }
}
