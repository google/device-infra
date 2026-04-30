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

package com.google.devtools.mobileharness.platform.android.shared.emulator;

import com.google.auto.value.AutoValue;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.Operation;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Preparer for local images and uploading them to Cloud Orchestrator. */
public class CloudOrchestratorLocalImagePreparer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CloudOrchestratorClient client;

  public CloudOrchestratorLocalImagePreparer(CloudOrchestratorClient client) {
    this.client = client;
  }

  /** Result of image preparation containing directory ID and checksums. */
  @AutoValue
  public abstract static class ImagePreparationResult {
    public abstract String imageDirId();

    public abstract String hostPkgChecksum();

    public abstract String deviceImgChecksum();

    public static ImagePreparationResult create(
        String imageDirId, String hostPkgChecksum, String deviceImgChecksum) {
      return new AutoValue_CloudOrchestratorLocalImagePreparer_ImagePreparationResult(
          imageDirId, hostPkgChecksum, deviceImgChecksum);
    }
  }

  /**
   * Prepares local images by uploading them to the host orchestrator if needed, and creating an
   * image directory.
   *
   * @param hostId the ID of the host where the images will be used
   * @param hostImage the local host image file (e.g., cvd-host_package.tar.gz)
   * @param deviceImage the local device image zip file
   * @return the result containing image directory ID and checksums
   * @throws MobileHarnessException if any API call fails
   * @throws IOException if any file operation fails
   * @throws InterruptedException if the thread is interrupted while waiting for operations
   */
  public ImagePreparationResult prepareImagesAndWait(
      String hostId, File hostImage, File deviceImage)
      throws MobileHarnessException, IOException, InterruptedException {
    Map<File, String> fileToChecksumMap = new HashMap<>();

    String hostImageChecksum = calculateChecksum(hostImage);
    fileToChecksumMap.put(hostImage, hostImageChecksum);
    uploadFileIfNeeded(hostId, hostImage, hostImageChecksum);

    String deviceImageChecksum = calculateChecksum(deviceImage);
    fileToChecksumMap.put(deviceImage, deviceImageChecksum);
    uploadFileIfNeeded(hostId, deviceImage, deviceImageChecksum);

    // Extract artifacts on the server
    extractArtifactIfNeeded(hostId, hostImageChecksum, hostImage.getName());
    extractArtifactIfNeeded(hostId, deviceImageChecksum, deviceImage.getName());

    // Create image directory and add artifacts to it
    String imageDirId = createImageDirectoryWithArtifacts(hostId, fileToChecksumMap.values());
    return ImagePreparationResult.create(imageDirId, hostImageChecksum, deviceImageChecksum);
  }

  private String calculateChecksum(File file) throws IOException {
    logger.atInfo().log("Calculating checksum for %s...", file.getName());
    return Files.asByteSource(file).hash(Hashing.sha256()).toString();
  }

  private void uploadFileIfNeeded(String hostId, File file, String checksum)
      throws MobileHarnessException, IOException {
    if (client.artifactExists(hostId, checksum)) {
      logger.atInfo().log("Artifact %s already exists on server, skipping upload.", checksum);
      return;
    }
    logger.atInfo().log("Uploading artifact %s...", file.getName());
    client.uploadArtifact(hostId, checksum, file);
  }

  private void extractArtifactIfNeeded(String hostId, String checksum, String artifactName)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Extracting %s on server...", artifactName);
    try {
      Operation op = client.extractArtifact(hostId, checksum);
      client.waitOperation(hostId, op.name, Map.class);
    } catch (MobileHarnessException e) {
      if (CloudOrchestratorClient.hasHttpStatusCode(e, 409)
          || e.getMessage().contains("already extracted")) {
        logger.atInfo().log("%s already extracted.", artifactName);
      } else {
        throw e;
      }
    }
  }

  private String createImageDirectoryWithArtifacts(String hostId, Collection<String> checksums)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Creating image directory on server...");
    Operation op = client.createImageDirectory(hostId);
    Map<?, ?> res = client.waitOperation(hostId, op.name, Map.class);
    String imageDirId = (String) res.get("id");

    for (String checksum : checksums) {
      logger.atInfo().log("Adding artifact %s to image directory %s...", checksum, imageDirId);
      Operation updateOp = client.updateImageDirectory(hostId, imageDirId, checksum);
      client.waitOperation(hostId, updateOp.name, Map.class);
    }
    return imageDirId;
  }
}
