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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.common.io.Files;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.Operation;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class CloudOrchestratorLocalImagePreparerTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private CloudOrchestratorClient mockClient;

  private CloudOrchestratorLocalImagePreparer manager;
  private File tempHostImage;
  private File tempDeviceImage;

  @Before
  public void setUp() throws Exception {
    manager = new CloudOrchestratorLocalImagePreparer(mockClient);
    tempHostImage = File.createTempFile("host", ".tar.gz");
    tempHostImage.deleteOnExit();
    Files.asByteSink(tempHostImage).write(new byte[] {1});

    tempDeviceImage = File.createTempFile("device", ".zip");
    tempDeviceImage.deleteOnExit();
    Files.asByteSink(tempDeviceImage).write(new byte[] {2});
  }

  @Test
  public void prepareImagesAndWait_success() throws Exception {
    String hostId = "host-1";

    // Mock artifactExists to false (need to upload)
    when(mockClient.artifactExists(eq(hostId), anyString())).thenReturn(false);

    // Mock extractArtifact
    Operation extractOp1 = new Operation();
    extractOp1.name = "op-extract-1";
    Operation extractOp2 = new Operation();
    extractOp2.name = "op-extract-2";
    when(mockClient.extractArtifact(eq(hostId), anyString())).thenReturn(extractOp1, extractOp2);

    // Mock waitOperation for extract
    when(mockClient.waitOperation(eq(hostId), eq("op-extract-1"), eq(Map.class)))
        .thenReturn(new HashMap<>());
    when(mockClient.waitOperation(eq(hostId), eq("op-extract-2"), eq(Map.class)))
        .thenReturn(new HashMap<>());

    // Mock createImageDirectory
    Operation createDirOp1 = new Operation();
    createDirOp1.name = "op-create-dir-1";
    Operation createDirOp2 = new Operation();
    createDirOp2.name = "op-create-dir-2";
    when(mockClient.createImageDirectory(hostId)).thenReturn(createDirOp1, createDirOp2);

    // Mock waitOperation for create dir
    Map<String, String> createDirRes1 = new HashMap<>();
    createDirRes1.put("id", "dir-host");
    Map<String, String> createDirRes2 = new HashMap<>();
    createDirRes2.put("id", "dir-device");
    when(mockClient.waitOperation(eq(hostId), eq("op-create-dir-1"), eq(Map.class)))
        .thenReturn(createDirRes1);
    when(mockClient.waitOperation(eq(hostId), eq("op-create-dir-2"), eq(Map.class)))
        .thenReturn(createDirRes2);

    // Mock updateImageDirectory
    Operation updateOp1 = new Operation();
    updateOp1.name = "op-update-1";
    Operation updateOp2 = new Operation();
    updateOp2.name = "op-update-2";
    when(mockClient.updateImageDirectory(eq(hostId), eq("dir-host"), anyString()))
        .thenReturn(updateOp1);
    when(mockClient.updateImageDirectory(eq(hostId), eq("dir-device"), anyString()))
        .thenReturn(updateOp2);

    // Mock waitOperation for update
    when(mockClient.waitOperation(eq(hostId), eq("op-update-1"), eq(Map.class)))
        .thenReturn(new HashMap<>());
    when(mockClient.waitOperation(eq(hostId), eq("op-update-2"), eq(Map.class)))
        .thenReturn(new HashMap<>());

    CloudOrchestratorLocalImagePreparer.ImagePreparationResult result =
        manager.prepareImagesAndWait(hostId, tempHostImage, tempDeviceImage);

    assertThat(result.hostImageDirId()).isEqualTo("dir-host");
    assertThat(result.deviceImageDirId()).isEqualTo("dir-device");

    // Verify upload was called
    verify(mockClient, times(2)).uploadArtifact(eq(hostId), anyString(), any(File.class));
    // Verify extract was called
    verify(mockClient, times(2)).extractArtifact(eq(hostId), anyString());
    // Verify update dir was called
    verify(mockClient).updateImageDirectory(eq(hostId), eq("dir-host"), anyString());
    verify(mockClient).updateImageDirectory(eq(hostId), eq("dir-device"), anyString());
  }

  @Test
  public void prepareImagesAndWait_skipUploadIfExist() throws Exception {
    String hostId = "host-1";

    // Mock artifactExists to true (skip upload)
    when(mockClient.artifactExists(eq(hostId), anyString())).thenReturn(true);

    // Mock other calls as above...
    Operation extractOp = new Operation();
    extractOp.name = "op-extract";
    when(mockClient.extractArtifact(eq(hostId), anyString())).thenReturn(extractOp);
    when(mockClient.waitOperation(eq(hostId), anyString(), eq(Map.class)))
        .thenReturn(new HashMap<>());

    Operation createDirOp1 = new Operation();
    createDirOp1.name = "op-create-dir-1";
    Operation createDirOp2 = new Operation();
    createDirOp2.name = "op-create-dir-2";
    when(mockClient.createImageDirectory(hostId)).thenReturn(createDirOp1, createDirOp2);
    Map<String, String> createDirRes1 = new HashMap<>();
    createDirRes1.put("id", "dir-host");
    Map<String, String> createDirRes2 = new HashMap<>();
    createDirRes2.put("id", "dir-device");
    when(mockClient.waitOperation(eq(hostId), eq("op-create-dir-1"), eq(Map.class)))
        .thenReturn(createDirRes1);
    when(mockClient.waitOperation(eq(hostId), eq("op-create-dir-2"), eq(Map.class)))
        .thenReturn(createDirRes2);

    Operation updateOp1 = new Operation();
    updateOp1.name = "op-update-1";
    when(mockClient.updateImageDirectory(eq(hostId), eq("dir-host"), anyString()))
        .thenReturn(updateOp1);
    Operation updateOp2 = new Operation();
    updateOp2.name = "op-update-2";
    when(mockClient.updateImageDirectory(eq(hostId), eq("dir-device"), anyString()))
        .thenReturn(updateOp2);

    CloudOrchestratorLocalImagePreparer.ImagePreparationResult result =
        manager.prepareImagesAndWait(hostId, tempHostImage, tempDeviceImage);

    assertThat(result.hostImageDirId()).isEqualTo("dir-host");
    assertThat(result.deviceImageDirId()).isEqualTo("dir-device");

    // Verify upload was NOT called
    verify(mockClient, never()).uploadArtifact(eq(hostId), anyString(), any(File.class));
  }

  @Test
  public void prepareImagesAndWait_extractArtifactThrows409_success() throws Exception {
    String hostId = "host-1";

    when(mockClient.artifactExists(eq(hostId), anyString())).thenReturn(true);

    HttpResponseException httpEx =
        new HttpResponseException.Builder(409, "Conflict", new HttpHeaders()).build();
    MobileHarnessException mhEx =
        new MobileHarnessException(BasicErrorId.LOCAL_NETWORK_ERROR, "Failed", httpEx);
    when(mockClient.extractArtifact(eq(hostId), anyString())).thenThrow(mhEx);

    Operation createDirOp1 = new Operation();
    createDirOp1.name = "op-create-dir-1";
    Operation createDirOp2 = new Operation();
    createDirOp2.name = "op-create-dir-2";
    when(mockClient.createImageDirectory(hostId)).thenReturn(createDirOp1, createDirOp2);

    Map<String, String> createDirRes1 = new HashMap<>();
    createDirRes1.put("id", "dir-host");
    Map<String, String> createDirRes2 = new HashMap<>();
    createDirRes2.put("id", "dir-device");
    when(mockClient.waitOperation(eq(hostId), eq("op-create-dir-1"), eq(Map.class)))
        .thenReturn(createDirRes1);
    when(mockClient.waitOperation(eq(hostId), eq("op-create-dir-2"), eq(Map.class)))
        .thenReturn(createDirRes2);

    Operation updateOp1 = new Operation();
    updateOp1.name = "op-update-1";
    when(mockClient.updateImageDirectory(eq(hostId), eq("dir-host"), anyString()))
        .thenReturn(updateOp1);
    Operation updateOp2 = new Operation();
    updateOp2.name = "op-update-2";
    when(mockClient.updateImageDirectory(eq(hostId), eq("dir-device"), anyString()))
        .thenReturn(updateOp2);

    when(mockClient.waitOperation(eq(hostId), eq("op-update-1"), eq(Map.class)))
        .thenReturn(new HashMap<>());
    when(mockClient.waitOperation(eq(hostId), eq("op-update-2"), eq(Map.class)))
        .thenReturn(new HashMap<>());

    CloudOrchestratorLocalImagePreparer.ImagePreparationResult result =
        manager.prepareImagesAndWait(hostId, tempHostImage, tempDeviceImage);

    assertThat(result.hostImageDirId()).isEqualTo("dir-host");
    assertThat(result.deviceImageDirId()).isEqualTo("dir-device");
  }
}
