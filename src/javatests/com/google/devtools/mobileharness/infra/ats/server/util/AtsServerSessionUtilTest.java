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

package com.google.devtools.mobileharness.infra.ats.server.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestResource;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import com.google.devtools.mobileharness.shared.util.network.NetworkUtil;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.Dimension;
import java.net.URL;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AtsServerSessionUtilTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final SetFlags flags = new SetFlags();
  @Bind @Mock private NetworkUtil networkUtil;
  @Bind @Mock private DeviceQuerier deviceQuerier;
  @Bind @Mock private LocalFileUtil localFileUtil;

  @Inject private AtsServerSessionUtil atsServerSessionUtil;

  @Before
  public void doBeforeEachTest() {
    flags.setAll(ImmutableMap.of("ats_storage_path", "/data"));
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void isLocalMode_sameHost() throws Exception {
    when(networkUtil.getLocalHostName()).thenReturn("host_name");
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_1")
                        .addType("AndroidOnlineDevice")
                        .addDimension(
                            Dimension.newBuilder().setName("host_name").setValue("host_name")))
                .build());
    assertThat(atsServerSessionUtil.isLocalMode()).isTrue();
  }

  @Test
  public void isLocalMode_differentHost() throws Exception {
    when(networkUtil.getLocalHostName()).thenReturn("host_name");
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_1")
                        .addType("AndroidOnlineDevice")
                        .addDimension(
                            Dimension.newBuilder().setName("host_name").setValue("host_name2")))
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_2")
                        .addType("AndroidOnlineDevice")
                        .addDimension(
                            Dimension.newBuilder().setName("host_name").setValue("host_name")))
                .build());
    assertThat(atsServerSessionUtil.isLocalMode()).isFalse();
  }

  @Test
  public void replacePathForRemoteRunner_alreadyAtsFileServerPrefix_returnsUnchanged()
      throws Exception {
    assertThat(atsServerSessionUtil.replacePathForRemoteRunner("ats-file-server::/path/to/file"))
        .isEqualTo("ats-file-server::/path/to/file");
  }

  @Test
  public void replacePathForRemoteRunner_gcsPrefix_replacesPrefix() throws Exception {
    assertThat(
            atsServerSessionUtil.replacePathForRemoteRunner(
                "mtt:///google_cloud_storage/bucket/path"))
        .isEqualTo("gs://bucket/path");
  }

  @Test
  public void replacePathForRemoteRunner_acloudOutsideStoragePath_copiesAndReplacesPrefix()
      throws Exception {
    when(localFileUtil.isFileOrDirExist("/data/mh_resources/acloud_prebuilt")).thenReturn(false);

    String result = atsServerSessionUtil.replacePathForRemoteRunner("/bin/acloud_prebuilt");

    assertThat(result).isEqualTo("ats-file-server::/mh_resources/acloud_prebuilt");
    verify(localFileUtil).prepareDir("/data/mh_resources");
    verify(localFileUtil)
        .copyFileOrDir("/bin/acloud_prebuilt", "/data/mh_resources/acloud_prebuilt");
  }

  @Test
  public void replacePathForRemoteRunner_regularPathUnderStoragePath_makesRelativeAndPrefixes()
      throws Exception {
    assertThat(atsServerSessionUtil.replacePathForRemoteRunner("/data/path/to/file1"))
        .isEqualTo("ats-file-server::/path/to/file1");
  }

  @Test
  public void getTestResourceUrl_validUrl_returnsUrl() throws Exception {
    TestResource resource = TestResource.newBuilder().setUrl("file:///data/path/to/file1").build();

    URL url = AtsServerSessionUtil.getTestResourceUrl(resource);

    assertThat(url.getProtocol()).isEqualTo("file");
    assertThat(url.getPath()).isEqualTo("/data/path/to/file1");
  }

  @Test
  public void getTestResourceUrl_invalidUrl_throwsException() {
    TestResource resource = TestResource.newBuilder().setUrl("invalid_url").build();

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class, () -> AtsServerSessionUtil.getTestResourceUrl(resource));
    assertThat(exception.getErrorId()).isEqualTo(InfraErrorId.ATS_SERVER_INVALID_TEST_RESOURCE);
  }

  @Test
  public void insertAdditionalTestResource_addsNonXtsZipFileResourcesToJobInfo() throws Exception {
    JobInfo jobInfo = mock(JobInfo.class);
    Files jobFiles = mock(Files.class);
    when(jobInfo.files()).thenReturn(jobFiles);

    atsServerSessionUtil.insertAdditionalTestResource(
        jobInfo,
        ImmutableList.of(
            TestResource.newBuilder()
                .setName("android-cts.zip")
                .setUrl("file:///data/path/to/android-cts.zip")
                .build(),
            TestResource.newBuilder()
                .setName("android-cts-media-1.5.zip")
                .setUrl("file:///data/path/to/android-cts-media-1.5.zip")
                .build(),
            TestResource.newBuilder()
                .setName("test-name-1")
                .setUrl("file:///data/path/to/file1")
                .build()));

    verify(jobFiles, never()).add(any(), eq("ats-file-server::/path/to/android-cts.zip"));
    verify(jobFiles)
        .add("android-cts-media-1.5.zip", "ats-file-server::/path/to/android-cts-media-1.5.zip");
    verify(jobFiles).add("test-name-1", "ats-file-server::/path/to/file1");
  }
}
