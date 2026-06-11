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

package com.google.devtools.mobileharness.shared.util.auth;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import java.util.Optional;

/** Utility for getting credential files. */
public class CredentialFileUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private CredentialFileUtil() {}

  /** Returns the credential file. */
  public static Optional<String> getDefaultCredentialFile() {
    Optional<String> explicitCredentialFile = getCredentialFileFromFlags();
    if (explicitCredentialFile.isPresent()) {
      return explicitCredentialFile;
    }
    return Optional.empty();
  }

  /** Returns the credential file specified by explicit flags. */
  public static Optional<String> getCredentialFileFromFlags() {
    LocalFileUtil localFileUtil = new LocalFileUtil();

    String fileTransferCredFile = Flags.fileTransferCredFile.get();
    if (fileTransferCredFile != null && localFileUtil.isFileExist(fileTransferCredFile)) {
      logger.atInfo().log(
          "Using local file from --file_transfer_cred_file as credential: %s",
          fileTransferCredFile);
      return Optional.of(fileTransferCredFile);
    }

    String internalServiceCredFile = Flags.internalServiceCredentialFile.get();
    if (internalServiceCredFile != null && localFileUtil.isFileExist(internalServiceCredFile)) {
      logger.atInfo().log(
          "Using local file from --internal_service_credential_file as credential: %s",
          internalServiceCredFile);
      return Optional.of(internalServiceCredFile);
    }
    return Optional.empty();
  }
}
