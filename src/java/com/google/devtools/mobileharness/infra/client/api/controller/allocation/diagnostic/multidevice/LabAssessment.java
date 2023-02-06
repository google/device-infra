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

package com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.multidevice;

import static java.lang.Math.max;
import static java.lang.Math.min;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.Assessment;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.singledevice.SingleDeviceAssessment;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.singledevice.SingleDeviceAssessor;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier.LabQueryResult;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Value;
import com.google.wireless.qa.mobileharness.shared.model.job.JobScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceInfo;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** An {@link Assessment} of a lab host and its ability to satisfy job requirements. */
public class LabAssessment implements Assessment<LabQueryResult> {

  private final JobScheduleUnit job;
  private final SingleDeviceAssessor assessor;
  private final Map<SubDeviceSpec, SingleDeviceAssessment> requirementsToOverallAssessments;
  private final ListMultimap<SubDeviceSpec, DeviceCandidate> requirementsToSortedCandidates;
  private String hostname;
  private Optional<Integer> score;

  public LabAssessment(JobScheduleUnit job) {
    this(job, new SingleDeviceAssessor());
  }

  @VisibleForTesting
  LabAssessment(JobScheduleUnit job, SingleDeviceAssessor assessor) {
    this.job = job;
    this.assessor = assessor;
    this.requirementsToOverallAssessments = new HashMap<>();
    this.requirementsToSortedCandidates = ArrayListMultimap.create();
    this.score = Optional.empty();
  }

  /**
   * Adds a lab and its devices to this assessment for scoring consideration. Since MH only supports
   * allocating devices from a single lab at a time, this should only be called once per Assessment.
   * In other words, only one lab should be added.
   */
  @CanIgnoreReturnValue
  @Override
  public LabAssessment addResource(LabQueryResult lab) {
    for (SubDeviceSpec spec : job.subDeviceSpecs().getAllSubDevices()) {
      requirementsToOverallAssessments.put(spec, assessor.assess(job, spec, lab.devices()));
      for (DeviceInfo device : lab.devices()) {
        if (!isPossibleCandidate(device)) {
          continue;
        }
        String id = device.locator().getSerial();
        SingleDeviceAssessment assessment = assessor.assess(job, spec, device);
        requirementsToSortedCandidates.put(spec, DeviceCandidate.create(id, assessment));
      }
      requirementsToSortedCandidates
          .get(spec)
          .sort((c1, c2) -> c2.assessment().getScore() - c1.assessment().getScore());
    }
    this.hostname = lab.hostname();
    return this;
  }

  private boolean isPossibleCandidate(DeviceInfo deviceInfo) {
    boolean deviceHasSimCardInfoDimension =
        deviceInfo.dimensions().supported().get(Name.SIM_CARD_INFO).stream()
            .anyMatch(value -> !value.equals(Value.NO_SIM));
    boolean jobHasSimCardInfoDimension = job.dimensions().get(Name.SIM_CARD_INFO) != null;
    if (!jobHasSimCardInfoDimension && deviceHasSimCardInfoDimension) {
      return false;
    }

    boolean deviceHasNonDefaultPoolNameDimension =
        deviceInfo.dimensions().supported().get(Name.POOL_NAME).stream()
            .anyMatch(value -> !Value.DEFAULT_POOL_NAME.equals(value));
    boolean jobHasNonDefaultPoolNameDimension =
        job.dimensions().get(Name.POOL_NAME) != null
            && !Value.DEFAULT_POOL_NAME.equals(job.dimensions().get(Name.POOL_NAME));
    if (!jobHasNonDefaultPoolNameDimension && deviceHasNonDefaultPoolNameDimension) {
      return false;
    }
    return true;
  }

  /**
   * @see {@link Assessment#getScore()}.
   *     <p>This value returned from this is cached and it is efficient to call getScore several
   *     times.
   */
  @Override
  public int getScore() {
    if (score.isEmpty()) {
      List<SubDeviceSpec> specs = job.subDeviceSpecs().getAllSubDevices();
      score = Optional.of(computeScore(specs, 0, new HashSet<>(), new HashSet<>()));
    }
    return score.get();
  }

  /**
   * Computes the score of this assessment.
   *
   * @param specs the device requirements
   * @param specIndex the index of the requirement in specs that is currently being matched for
   *     scoring
   * @param currentCandidateSet the current set of candidate devices already matched to requirements
   * @param currentSerials the current set of device serials already matched to requirements
   */
  private int computeScore(
      List<SubDeviceSpec> specs,
      int specIndex,
      Set<DeviceCandidate> currentCandidateSet,
      Set<String> currentSerials) {
    if (currentCandidateSet.size() == specs.size()) {
      int sum = 0;
      for (DeviceCandidate candidate : currentCandidateSet) {
        sum += candidate.assessment().getScore();
      }
      return sum;
    }

    List<DeviceCandidate> candidates = requirementsToSortedCandidates.get(specs.get(specIndex));
    int bestScore = 0;
    int searchDepth =
        min(2, candidates.size()); // Select at most 2 candidates to reduce running time.
    for (int i = 0; i < searchDepth; i++) {
      DeviceCandidate candidate = candidates.get(i);
      if (currentSerials.contains(candidate.id())) {
        continue;
      }
      currentCandidateSet.add(candidate);
      currentSerials.add(candidate.id());
      bestScore =
          max(bestScore, computeScore(specs, specIndex + 1, currentCandidateSet, currentSerials));
      currentCandidateSet.remove(candidate);
      currentSerials.remove(candidate.id());
    }

    return bestScore;
  }

  /** Returns whether this {@link Assessment} has the maximum possible score. */
  public boolean hasMaxScore() {
    return getScore()
        == (SingleDeviceAssessment.MAX_SCORE * job.subDeviceSpecs().getSubDeviceCount());
  }

  /** Returns the hostname of the lab resource under consideration. */
  String getHostname() {
    return hostname;
  }

  /**
   * Returns the overall {@link SingleDeviceAssessment} for the given spec. This overall assessment
   * determines whether there are any specific requirements (dimensions, decorators, availability,
   * etc.) that cannot be satisfied with any devices at the lab host under evaluation. If the
   * returned assessment has a maximum score, this does not imply the spec can be matched, but
   * rather that each requirement in the spec is satisfied by some device.
   */
  SingleDeviceAssessment getOverallDeviceAssessment(SubDeviceSpec spec) {
    return requirementsToOverallAssessments.get(spec);
  }

  /**
   * Returns the top limit {@link DeviceCandidates} for the given spec. The returned list will be
   * sorted in non-increasing order by {@link SingleDeviceAssessment#getScore()}.
   */
  ImmutableList<DeviceCandidate> getTopCandidates(SubDeviceSpec spec, int limit) {
    List<DeviceCandidate> candidates = requirementsToSortedCandidates.get(spec);
    if (candidates.size() > limit) {
      return ImmutableList.copyOf(candidates.subList(0, limit));
    }
    return ImmutableList.copyOf(candidates);
  }

  @AutoValue
  abstract static class DeviceCandidate {
    public abstract String id();

    public abstract SingleDeviceAssessment assessment();

    public static DeviceCandidate create(String id, SingleDeviceAssessment assessment) {
      return new AutoValue_LabAssessment_DeviceCandidate(id, assessment);
    }
  }
}
