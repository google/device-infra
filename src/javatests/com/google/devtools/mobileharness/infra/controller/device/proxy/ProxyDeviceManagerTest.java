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

package com.google.devtools.mobileharness.infra.controller.device.proxy;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Correspondence.transforming;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.job.TestLocator;
import com.google.devtools.mobileharness.infra.controller.device.proxy.ProxyDeviceManager.ProxyDevices;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfoMocker;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProxyDeviceManagerTest {

  @Bind
  private final ListeningExecutorService threadPool =
      ThreadPools.createStandardThreadPool("testing-thread-pool");

  private final JobInfo jobInfo = JobInfoMocker.mockJobInfo();

  @Inject private ProxyDeviceManager proxyDeviceManager;

  @Before
  public void setUp() throws Exception {
    Guice.createInjector(BoundFieldModule.of(this), new ProxyDeviceManagerModule())
        .injectMembers(this);

    jobInfo.subDeviceSpecs().getSubDevice(0).dimensions().add("id", "no-op-device-0");
    for (int i = 1; i < 5; i++) {
      jobInfo
          .subDeviceSpecs()
          .addSubDevice(JobInfoMocker.FAKE_JOB_DEVICE, ImmutableMap.of("id", "no-op-device-" + i));
    }

    jobInfo.tests().add("fake-test-id-1", "fake-test-name-1");
    jobInfo.tests().add("fake-test-id-2", "fake-test-name-2");
  }

  @Test
  public void leaseDevicesOfJobAsync() throws Exception {
    ImmutableSet<TestLocator> testLocators =
        jobInfo.tests().getAll().values().stream()
            .map(testInfo -> testInfo.locator().toNewTestLocator())
            .collect(toImmutableSet());
    TestLocator testLocator1 =
        requireNonNull(jobInfo.tests().getById("fake-test-id-1")).locator().toNewTestLocator();

    ImmutableMap<TestLocator, ListenableFuture<ProxyDevices>> result =
        proxyDeviceManager.leaseDevicesOfJobAsync(
            jobInfo.locator().toNewJobLocator(),
            ImmutableMap.of(
                0,
                ProxyDeviceRequirement.of(jobInfo.subDeviceSpecs(), 0),
                2,
                ProxyDeviceRequirement.of(jobInfo.subDeviceSpecs(), 2),
                4,
                ProxyDeviceRequirement.of(jobInfo.subDeviceSpecs(), 4)),
            testLocators);

    assertThat(result.keySet()).containsExactlyElementsIn(testLocators);
    assertThat(requireNonNull(result.get(testLocator1)).get().devices())
        .comparingValuesUsing(
            transforming(
                (Device device) -> requireNonNull(device).getDeviceId(), "has a control ID of"))
        .containsExactly(
            0, "no-op-device-0",
            2, "no-op-device-2",
            4, "no-op-device-4");

    proxyDeviceManager.releaseDevicesOfJob(jobInfo.locator().toNewJobLocator());
  }
}
