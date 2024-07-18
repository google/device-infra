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

package com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.common;

import com.google.devtools.mobileharness.shared.util.flags.Flags;
import java.time.Duration;

/** Constant of Cloud file transfer framework. */
public final class FileTransferConstant {

  /** size of a Megabytes. */
  private static final int MEGA_BYTE = 1 << 20;

  private static final int KILO_BYTE = 1 << 10;

  public static Duration getCloudCacheTtl() {
    return Flags.instance().fileTransferCloudCacheTtl.getNonNull();
  }

  public static Duration getLocalCacheTtl() {
    return Flags.instance().fileTransferLocalCacheTtl.getNonNull();
  }

  public static String getBucket() {
    return Flags.instance().fileTransferBucket.getNonNull();
  }

  public static int getMaximumAttempts() {
    return Flags.instance().cloudFileTransferMaximumAttempts.getNonNull();
  }

  public static Duration getTimeout() {
    return Flags.instance().cloudFileTransferTimeout.getNonNull();
  }

  public static long uploadShardSize() {
    return Long.valueOf(Flags.instance().cloudFileTransferUploadShardSize.getNonNull()) * MEGA_BYTE;
  }

  public static long downloadShardSize() {
    return Long.valueOf(Flags.instance().cloudFileTransferDownloadShardSize.getNonNull())
        * MEGA_BYTE;
  }

  public static Duration getInitialTimeout() {
    return Flags.instance().cloudFileTransferInitialTimeout.getNonNull();
  }

  public static long getSmallFileSize() {
    return Flags.instance().cloudFileTransferSmallFileSizeKb.getNonNull() * KILO_BYTE;
  }

  private FileTransferConstant() {}
}
