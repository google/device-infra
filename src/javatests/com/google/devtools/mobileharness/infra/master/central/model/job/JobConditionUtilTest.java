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

package com.google.devtools.mobileharness.infra.master.central.model.job;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.infra.master.central.proto.Job.JobCondition;
import com.google.devtools.mobileharness.infra.master.central.proto.Job.JobCondition.JobStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link JobConditionUtil}. */
@RunWith(JUnit4.class)
public class JobConditionUtilTest {
  @Test
  public void isExpired_runningTest_false() {
    JobCondition jobCondition =
        JobCondition.newBuilder()
            .setHeartbeatTimeMs(
                Instant.now().minus(Duration.ofMinutes(5)).minusMillis(1L).toEpochMilli())
            .setStatus(JobStatus.RUNNING)
            .setKeepAliveTimeoutMs(Duration.ofMinutes(15).toMillis())
            .build();
    assertThat(JobConditionUtil.isExpired(jobCondition)).isFalse();
  }

  @Test
  public void isExpired_runningTest_true() {
    JobCondition jobCondition =
        JobCondition.newBuilder()
            .setHeartbeatTimeMs(
                Instant.now().minus(Duration.ofMinutes(15)).minusMillis(1L).toEpochMilli())
            .setStatus(JobStatus.RUNNING)
            .setKeepAliveTimeoutMs(Duration.ofMinutes(15).toMillis())
            .build();
    assertThat(JobConditionUtil.isExpired(jobCondition)).isTrue();
  }

  @Test
  public void isExpired_closedTest_true() {
    JobCondition jobCondition =
        JobCondition.newBuilder()
            .setHeartbeatTimeMs(
                Instant.now().minus(Duration.ofMinutes(5)).minusMillis(1L).toEpochMilli())
            .setStatus(JobStatus.DONE)
            .setKeepAliveTimeoutMs(Duration.ofMinutes(15).toMillis())
            .build();
    assertThat(JobConditionUtil.isExpired(jobCondition)).isTrue();
  }

  @Test
  public void isExpired_closedTest_false() {
    JobCondition jobCondition =
        JobCondition.newBuilder()
            .setHeartbeatTimeMs(Instant.now().toEpochMilli())
            .setStatus(JobStatus.DONE)
            .build();
    assertThat(JobConditionUtil.isExpired(jobCondition)).isFalse();
  }

  @Test
  public void isClosed_false() {
    assertThat(
            JobConditionUtil.isClosed(JobCondition.newBuilder().setStatus(JobStatus.UNSET).build()))
        .isFalse();
    assertThat(
            JobConditionUtil.isClosed(
                JobCondition.newBuilder().setStatus(JobStatus.RUNNING).build()))
        .isFalse();
  }

  @Test
  public void isClosed_true() {
    assertThat(
            JobConditionUtil.isClosed(JobCondition.newBuilder().setStatus(JobStatus.DONE).build()))
        .isTrue();
    assertThat(
            JobConditionUtil.isClosed(
                JobCondition.newBuilder().setStatus(JobStatus.KILLED).build()))
        .isTrue();
  }
}
