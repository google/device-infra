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

package com.google.devtools.mobileharness.shared.util.file.remote;

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.mobileharness.shared.util.auth.CredentialFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import java.util.Optional;

/** Utility for getting GCS credential type. */
public final class GcsCredentialUtil {

  private GcsCredentialUtil() {}

  /**
   * Gets the credential type for GCS access.
   *
   * @param bucketName name of bucket to manage
   */
  public static GcsUtil.CredentialType getCredentialType(String bucketName) {
    return getCredentialType(bucketName, new SystemUtil());
  }

  @VisibleForTesting
  static GcsUtil.CredentialType getCredentialType(String bucketName, SystemUtil systemUtil) {

    Optional<String> credentialFile = CredentialFileUtil.getFileTransferCredentialFile();
    if (bucketName.equals(Flags.fileTransferBucket.get()) && credentialFile.isPresent()) {
      // If the bucket is the file transfer bucket and the credential file is provided, use the
      // credential file.
      return GcsUtil.CredentialType.ofCredentialFile(credentialFile.get());
    }

    // Otherwise, use app default credential.
    return GcsUtil.CredentialType.ofAppDefault();
  }
}
