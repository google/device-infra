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

package com.google.devtools.mobileharness.infra.master.central.model.lab;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.infra.master.central.proto.Lab.LabServerCondition;
import com.google.devtools.mobileharness.infra.master.central.proto.Lab.LabServerProfile;
import java.time.Instant;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link LabConditionUtil}. */
@RunWith(JUnit4.class)
public class LabConditionUtilTest {

  @Test
  public void isExpired_labServer_true() {
    LabServerCondition labServerCondition =
        LabServerCondition.newBuilder()
            .setTimestampMs(
                Instant.now()
                    .minus(LabConditionUtil.getExpirationThreshold())
                    .minusSeconds(1L)
                    .toEpochMilli())
            .build();
    assertThat(LabConditionUtil.isExpired(labServerCondition)).isTrue();
  }

  @Test
  public void isExpired_labServer_false() {
    LabServerCondition labServerCondition =
        LabServerCondition.newBuilder().setTimestampMs(Instant.now().toEpochMilli()).build();
    assertThat(LabConditionUtil.isExpired(labServerCondition)).isFalse();
  }

  @Test
  public void shouldRemove_labServer_true() {
    LabServerCondition labServerCondition =
        LabServerCondition.newBuilder()
            .setIsMissing(true)
            .setTimestampMs(
                Instant.now()
                    .minus(LabConditionUtil.getRemovalThreshold())
                    .minusSeconds(1L)
                    .toEpochMilli())
            .build();
    assertThat(
            LabConditionUtil.shouldRemove(
                LabDao.create(
                    LabLocator.LOCALHOST,
                    Optional.of(LabServerProfile.getDefaultInstance()),
                    Optional.of(labServerCondition))))
        .isTrue();
  }

  @Test
  public void shouldRemove_labServer_notMissing() {
    LabServerCondition labServerCondition =
        LabServerCondition.newBuilder()
            .setIsMissing(false)
            .setTimestampMs(
                Instant.now()
                    .minus(LabConditionUtil.getRemovalThreshold())
                    .minusSeconds(1L)
                    .toEpochMilli())
            .build();
    assertThat(
            LabConditionUtil.shouldRemove(
                LabDao.create(
                    LabLocator.LOCALHOST,
                    Optional.of(LabServerProfile.getDefaultInstance()),
                    Optional.of(labServerCondition))))
        .isFalse();
  }

  @Test
  public void shouldRemove_labServer_tooShort() {
    LabServerCondition labServerCondition =
        LabServerCondition.newBuilder()
            .setIsMissing(true)
            .setTimestampMs(
                Instant.now()
                    .minus(LabConditionUtil.getRemovalThreshold())
                    .plusSeconds(100L)
                    .toEpochMilli())
            .build();
    assertThat(
            LabConditionUtil.shouldRemove(
                LabDao.create(
                    LabLocator.LOCALHOST,
                    Optional.of(LabServerProfile.getDefaultInstance()),
                    Optional.of(labServerCondition))))
        .isFalse();
  }
}
