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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.lab.proto.LabFileMetadataList;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.protobuf.TextFormat;
import com.google.wireless.qa.mobileharness.shared.constant.DirCommon;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Utility class for processing files containing metadata of gcs files. */
public class GcsMetadataUtil {

  private final LocalFileUtil localFileUtil;

  public GcsMetadataUtil() {
    this(new LocalFileUtil());
  }

  public GcsMetadataUtil(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
  }

  /**
   * Writes the list of file metadatas to lab_file_metadata.pb file in the given output directory.
   * Suggested to use the TestInfo.getGenFileDir() as output directory.
   */
  public void writeGcsFileMetadata(LabFileMetadataList metadatas, Path outputDir)
      throws MobileHarnessException, InterruptedException {
    localFileUtil.prepareDir(outputDir);
    Path metadataFile = outputDir.resolve(DirCommon.LAB_FILE_METADATA_FILE_NAME);
    try (BufferedWriter writer = Files.newBufferedWriter(metadataFile.toFile().toPath(), UTF_8)) {
      TextFormat.printer().print(metadatas, writer);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.GCS_METADATA_WRITE_ERROR,
          "Failed to write lab file metadata to " + metadataFile,
          e);
    }
  }

  /**
   * Reads the list of file metadatas from lab_file_metadata.txtpb file in the given output
   * directory.
   */
  public LabFileMetadataList readGcsFileMetadata(Path metadataFile) throws MobileHarnessException {
    try {
      String fileContent = localFileUtil.readFile(metadataFile);
      LabFileMetadataList.Builder metadataListBuilder = LabFileMetadataList.newBuilder();
      TextFormat.getParser().merge(fileContent, metadataListBuilder);
      return metadataListBuilder.build();
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.GCS_METADATA_PARSE_ERROR,
          "Failed to parse lab file metadata from " + metadataFile,
          e);
    }
  }
}
