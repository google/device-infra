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

package com.google.devtools.mobileharness.infra.monitoring;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class BatchPipelineServiceTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private DataPusher pusher;
  @Mock private DataPuller<JobType> puller;

  private BatchPipelineService<JobType> service;

  @Before
  public void setUp() {
    service = new BatchPipelineService<>(puller, pusher);
  }

  @Test
  public void startUp_setUpPusherAndPuller() throws Exception {
    service.startUp();

    verify(pusher).setUp();
    verify(puller).setUp();
  }

  @Test
  public void shutDown_tearDownPusherAndPuller() throws Exception {
    service.shutDown();

    verify(pusher).tearDown();
    verify(puller).tearDown();
  }

  @Test
  public void runOneIteration_success() throws Exception {
    when(puller.pull()).thenReturn(ImmutableList.of());

    service.runOneIteration();

    verify(pusher).push(ImmutableList.of());
  }

  @Test
  public void runOneIteration_catchException() throws Exception {
    when(puller.pull())
        .thenThrow(new MobileHarnessException(InfraErrorId.MONITORING_PULL_DATA_ERROR, "mock"));

    service.runOneIteration();
  }
}
