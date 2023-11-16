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

package com.google.devtools.mobileharness.infra.ats.console.util.tradefed;

import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import javax.inject.Inject;

/** Util to write test record. */
public class TestRecordWriter {

  private final LocalFileUtil localFileUtil;

  @Inject
  TestRecordWriter(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
  }

  /**
   * Writes the {@code testRecord} to file {@code destinationPath}.
   *
   * @param useDelimitedApi Use {@code Proto.useDelimitedApi} to save proto, otherwise use default
   *     api
   * @throws MobileHarnessException if failed to write the test record to given file
   */
  public void writeTestRecordProto(
      TestRecord testRecord, Path destinationPath, boolean useDelimitedApi)
      throws MobileHarnessException {
    FileOutputStream output = null;
    Path tmpFile = null;
    try {
      Path parentDir = destinationPath.getParent();
      localFileUtil.prepareDir(parentDir);

      tmpFile = localFileUtil.createTempFile(destinationPath.getParent(), "tmp-proto", "");

      // Write to the tmp file
      output = new FileOutputStream(tmpFile.toFile());
      if (useDelimitedApi) {
        testRecord.writeDelimitedTo(output);
      } else {
        testRecord.writeTo(output);
      }

      // Move the tmp file to the new name when done writing.
      tmpFile.toFile().renameTo(destinationPath.toFile());
    } catch (IOException e) {
      throw new MobileHarnessException(
          ExtErrorId.TEST_RECORD_WRITE_PROTO_FILE_ERROR,
          String.format("Failed to write test record to file [%s]", destinationPath),
          e);
    } finally {
      close(output);
    }
  }

  /**
   * Closes the given {@link Closeable}.
   *
   * @param closeable the {@link Closeable}. No action taken if {@code null}.
   */
  private static void close(Closeable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (IOException e) {
        // ignore
      }
    }
  }
}
