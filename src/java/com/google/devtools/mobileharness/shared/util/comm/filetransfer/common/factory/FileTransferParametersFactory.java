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

import static com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.factory.FileTransferParameters.DEFAULT_ZIP_TIMEOUT;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import java.nio.file.Path;
import java.time.Duration;

/** Factories for creating {@link FileTransferParameters} from a {@link TestInfo}. */
public class FileTransferParametersFactory {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Whether enable cloud file transfer. */
  public static final String PARAM_ENABLE_CLOUD_FILE_TRANSFER = "enable_cloud_file_transfer";

  /** Whether only store all files into one single zip file without any compression. */
  private static final String PARAM_FILE_TRANSFER_ZIP_STORE_ONLY = "file_transfer_zip_store_only";

  /** Timeout of zipping a file during file transfer. */
  private static final String PARAM_ZIP_TIMEOUT_SEC = "file_transfer_zip_timeout_sec";

  /** Attempts of transferring a file if failed. */
  private static final String PARAM_ATTEMPTS = "file_transfer_maximum_attempts";

  /** Size of shard during file uploading. */
  private static final String PARAMS_UPLOAD_SHARD_SIZE_MB = "file_transfer_upload_shard_size_mb";

  /** Size of shard during file downloading. */
  private static final String PARAMS_DOWNLOAD_SHARD_SIZE_MB =
      "file_transfer_download_shard_size_mb";

  /** Timeout of transferring a file. */
  private static final String PARAMS_TIMEOUT_SEC = "file_transfer_timeout_sec";

  /** Timeout of checking whether current process is running as a role account or TVC. */
  private static final Duration ROLE_ACCOUNT_CHECK_TIMEOUT = Duration.ofSeconds(5);

  /** size of a Megabytes. */
  private static final int MEGA_BYTE = 1 << 20;

  /** Default name of file transfer client. */
  private static final String DEFAULT_FILE_TRANSFER_HOME_DIR_NAME = "file_transfer";

  public FileTransferParametersFactory() {}

  public FileTransferParameters create(TestInfo testInfo) throws MobileHarnessException {
    Params params = testInfo.jobInfo().params();
    FileTransferParameters.Builder fileTransferParams = FileTransferParameters.builder();

    boolean enableCloudFileTransfer;
    if (params.has(PARAM_ENABLE_CLOUD_FILE_TRANSFER)) {
      enableCloudFileTransfer = params.getBool(PARAM_ENABLE_CLOUD_FILE_TRANSFER, false);
    } else {
      enableCloudFileTransfer = false;
    }

    fileTransferParams.setEnableCloudFileTransfer(enableCloudFileTransfer);

    if (params.has(PARAM_ATTEMPTS)) {
      fileTransferParams.setAttempts(params.getInt(PARAM_ATTEMPTS, 0));
    }

    if (params.has(PARAMS_UPLOAD_SHARD_SIZE_MB)) {
      int paramsUploadShardSizeMb = params.getInt(PARAMS_UPLOAD_SHARD_SIZE_MB, 0);
      if (paramsUploadShardSizeMb < 2048 && paramsUploadShardSizeMb > 0) {
        fileTransferParams.setUploadShardSize(paramsUploadShardSizeMb * ((long) MEGA_BYTE));
      } else {
        logger.atWarning().log(
            "%s must be bigger than 0 and smaller than 2048. Ignore it.",
            PARAMS_UPLOAD_SHARD_SIZE_MB);
      }
    }

    if (params.has(PARAMS_DOWNLOAD_SHARD_SIZE_MB)) {
      int paramsDownloadShardSizeMb = params.getInt(PARAMS_DOWNLOAD_SHARD_SIZE_MB, 0);
      if (paramsDownloadShardSizeMb < 2048 && paramsDownloadShardSizeMb > 0) {
        fileTransferParams.setDownloadShardSize(paramsDownloadShardSizeMb * ((long) MEGA_BYTE));
      } else {
        logger.atWarning().log(
            "%s must be bigger than 0 and smaller than 2048. Ignore it.",
            PARAMS_DOWNLOAD_SHARD_SIZE_MB);
      }
    }

    if (params.has(PARAMS_TIMEOUT_SEC)) {
      fileTransferParams.setTimeout(Duration.ofSeconds(params.getLong(PARAMS_TIMEOUT_SEC, 0)));
    }

    if (params.has(PARAM_FILE_TRANSFER_ZIP_STORE_ONLY)) {
      fileTransferParams.setZipStoreOnly(params.getBool(PARAM_FILE_TRANSFER_ZIP_STORE_ONLY, false));
    }

    fileTransferParams
        .setZipTimeout(
            Duration.ofSeconds(
                params.getLong(PARAM_ZIP_TIMEOUT_SEC, DEFAULT_ZIP_TIMEOUT.toSeconds())))
        .setHomeDir(Path.of(testInfo.getTmpFileDir()).resolve(DEFAULT_FILE_TRANSFER_HOME_DIR_NAME));

    params
        .getOptional(JobInfo.PARAM_CLOUD_FILE_TRANSFER_BUCKET)
        .ifPresent(fileTransferParams::setCloudFileTransferBucket);
    return fileTransferParams.build();
  }
}
