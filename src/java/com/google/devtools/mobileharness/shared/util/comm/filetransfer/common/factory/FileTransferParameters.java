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

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.common.FileTransferConstant;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.nio.file.Path;
import java.time.Duration;

/** Parameters to create a file transfer client. */
@AutoValue
public abstract class FileTransferParameters {

  /** Default zip timeout. */
  public static final Duration DEFAULT_ZIP_TIMEOUT = Duration.ofMinutes(10);

  /** Default value of parameters. */
  public static final FileTransferParameters DEFAULT = builder().build();

  /** Whether compress files during zip. Its default value is true. */
  public abstract boolean zipStoreOnly();

  /** Size of shard during file uploading. */
  public abstract long uploadShardSize();

  /** Size of shard during file downloading. */
  public abstract long downloadShardSize();

  /** Timeout of zipping a file. It is {@link #DEFAULT_ZIP_TIMEOUT} by default. */
  public abstract Duration zipTimeout();

  /** Size limitation of a *small* file, which will be transferred between server and client. */
  public abstract long smallFileSize();

  /** Attempts of transferring a file if failed. */
  public abstract int attempts();

  /** Timeout of transferring a file. */
  public abstract Duration timeout();

  public abstract boolean enableCloudFileTransfer();

  /** Home directory of the file transfer client. */
  public abstract Path homeDir();

  public abstract String cloudFileTransferBucket();

  public static Builder builder() {
    return new AutoValue_FileTransferParameters.Builder()
        .setZipStoreOnly(false)
        .setZipTimeout(DEFAULT_ZIP_TIMEOUT)
        .setUploadShardSize(FileTransferConstant.uploadShardSize())
        .setDownloadShardSize(FileTransferConstant.downloadShardSize())
        .setSmallFileSize(FileTransferConstant.getSmallFileSize())
        .setAttempts(FileTransferConstant.getMaximumAttempts())
        .setTimeout(FileTransferConstant.getTimeout())
        .setEnableCloudFileTransfer(true)
        .setCloudFileTransferBucket(FileTransferConstant.getBucket())
        .setHomeDir(createHomeDir());
  }

  private static Path createHomeDir() {
    try {
      LocalFileUtil localFileUtil = new LocalFileUtil();
      return Path.of(localFileUtil.createTempDir("/tmp"));
    } catch (MobileHarnessException e) {
      throw new IllegalStateException("Failed to create home dir", e);
    }
  }

  /** Builder of {@link FileTransferParameters}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setZipStoreOnly(boolean storeOnly);

    public abstract Builder setZipTimeout(Duration timeout);

    public abstract Builder setEnableCloudFileTransfer(boolean enable);

    public abstract Builder setUploadShardSize(long uploadShardSize);

    public abstract Builder setDownloadShardSize(long downloadShardSize);

    public abstract Builder setSmallFileSize(long smallFileSize);

    public abstract Builder setAttempts(int attempts);

    public abstract Builder setTimeout(Duration timeout);

    public abstract Builder setHomeDir(Path homeDir);

    public abstract Builder setCloudFileTransferBucket(String bucket);

    public abstract FileTransferParameters build();
  }
}
