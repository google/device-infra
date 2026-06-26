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
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import javax.annotation.Nullable;

/** Utility for getting GCS credential type. */
public final class GcsCredentialUtil {

  private GcsCredentialUtil() {}

  /**
   * Gets the credential type for GCS file transfer based on bucket name.
   *
   * @param bucketName name of bucket to manage
   */
  public static GcsUtil.CredentialType getFileTransferCredentialType(String bucketName) {
    String credentialFile =
        CredentialFileUtil.getFileTransferCredentialFile(bucketName).orElse(null);
    return getCredentialType(null, credentialFile, new SystemUtil());
  }

  /**
   * Gets the credential type for GCS file resolver access.
   *
   * @param serviceAccount the service account to use, or null if none
   */
  public static GcsUtil.CredentialType getGcsResolverCredentialType(
      @Nullable String serviceAccount) {
    String credentialFile = CredentialFileUtil.getGcsResolverCredentialFile().orElse(null);
    return getCredentialType(serviceAccount, credentialFile, new SystemUtil());
  }

  /** Gets the default GCS credential type. */
  public static GcsUtil.CredentialType getDefaultCredentialType() {
    String credentialFile = CredentialFileUtil.getDefaultGcsCredentialFile().orElse(null);
    return getCredentialType(null, credentialFile, new SystemUtil());
  }

  /**
   * Gets the credential type for GCS access.
   *
   * @param serviceAccount service account to use on Borg
   * @param credentialFile optional credential file path
   * @param systemUtil util for system interaction
   */
  @VisibleForTesting
  static GcsUtil.CredentialType getCredentialType(
      @Nullable String serviceAccount, @Nullable String credentialFile, SystemUtil systemUtil) {

    if (credentialFile != null && !credentialFile.isEmpty()) {
      return GcsUtil.CredentialType.ofCredentialFile(credentialFile);
    }

    // Otherwise, use app default credential.
    return GcsUtil.CredentialType.ofAppDefault();
  }
}
