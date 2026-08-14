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

package com.google.devtools.mobileharness.shared.labinfo.diagnostic;

import static java.lang.Math.max;
import static java.lang.Math.min;

import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Job.DeviceRequirement;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.DiagnoseJobSpec;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Value;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** An assessment of a lab host and its ability to satisfy job requirements. */
public class MasterLabAssessment {

  private final DiagnoseJobSpec spec;
  private final Map<DeviceRequirement, MasterSingleDeviceAssessment>
      requirementsToOverallAssessments;
  private final ListMultimap<DeviceRequirement, DeviceCandidate> requirementsToSortedCandidates;
  private String hostname;
  private Optional<Integer> score;

  public MasterLabAssessment(DiagnoseJobSpec spec) {
    this.spec = spec;
    this.requirementsToOverallAssessments = new HashMap<>();
    this.requirementsToSortedCandidates = ArrayListMultimap.create();
    this.score = Optional.empty();
  }

  public MasterLabAssessment addResource(LabData labData) {
    List<DeviceInfo> devices = labData.getDeviceList().getDeviceInfoList();
    List<DeviceRequirement> requirements = spec.getDeviceRequirements().getDeviceRequirementList();

    for (DeviceRequirement req : requirements) {
      requirementsToOverallAssessments.put(req, assessDevices(spec, req, devices));
      for (DeviceInfo device : devices) {
        if (!isPossibleCandidate(device, spec)) {
          continue;
        }
        String id = device.getDeviceLocator().getId();
        MasterSingleDeviceAssessment assessment = assessDevice(spec, req, device);
        requirementsToSortedCandidates.put(req, DeviceCandidate.create(id, assessment));
      }
      requirementsToSortedCandidates
          .get(req)
          .sort((c1, c2) -> c2.assessment().getScore() - c1.assessment().getScore());
    }
    this.hostname = labData.getLabInfo().getLabLocator().getHostName();
    return this;
  }

  private MasterSingleDeviceAssessment assessDevice(
      DiagnoseJobSpec spec, DeviceRequirement req, DeviceInfo device) {
    return new MasterSingleDeviceAssessment(spec, req).addResource(device);
  }

  private MasterSingleDeviceAssessment assessDevices(
      DiagnoseJobSpec spec, DeviceRequirement req, List<DeviceInfo> devices) {
    MasterSingleDeviceAssessment assessment = new MasterSingleDeviceAssessment(spec, req);
    devices.forEach(assessment::addResource);
    return assessment;
  }

  private boolean isPossibleCandidate(DeviceInfo deviceInfo, DiagnoseJobSpec spec) {
    DeviceCompositeDimension deviceDimensions =
        deviceInfo.getDeviceFeature().getCompositeDimension();

    boolean deviceHasSimCardInfoDimension =
        getDimensionValues(deviceDimensions, Ascii.toLowerCase(Name.SIM_CARD_INFO.name())).stream()
            .anyMatch(value -> !value.equals(Value.NO_SIM));
    boolean jobHasSimCardInfoDimension =
        hasJobDimension(spec, Ascii.toLowerCase(Name.SIM_CARD_INFO.name()));
    if (!jobHasSimCardInfoDimension && deviceHasSimCardInfoDimension) {
      return false;
    }

    boolean deviceHasNonDefaultPoolNameDimension =
        getDimensionValues(deviceDimensions, Ascii.toLowerCase(Name.POOL_NAME.name())).stream()
            .anyMatch(value -> !Value.DEFAULT_POOL_NAME.equals(value));
    boolean jobHasNonDefaultPoolNameDimension =
        hasJobDimension(spec, Ascii.toLowerCase(Name.POOL_NAME.name()))
            && !Value.DEFAULT_POOL_NAME.equals(
                getJobDimensionValue(spec, Ascii.toLowerCase(Name.POOL_NAME.name())));
    if (!jobHasNonDefaultPoolNameDimension && deviceHasNonDefaultPoolNameDimension) {
      return false;
    }
    return true;
  }

  private static boolean hasJobDimension(DiagnoseJobSpec spec, String dimensionName) {
    return spec.getDeviceRequirements().getDeviceRequirementList().stream()
        .anyMatch(req -> req.getDimensionsMap().containsKey(dimensionName));
  }

  private static String getJobDimensionValue(DiagnoseJobSpec spec, String dimensionName) {
    for (DeviceRequirement req : spec.getDeviceRequirements().getDeviceRequirementList()) {
      String value = req.getDimensionsMap().get(dimensionName);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private static List<String> getDimensionValues(
      DeviceCompositeDimension deviceDimensions, String dimensionName) {
    List<String> values = new ArrayList<>();
    for (DeviceDimension dim : deviceDimensions.getSupportedDimensionList()) {
      if (dim.getName().equals(dimensionName)) {
        values.add(dim.getValue());
      }
    }
    for (DeviceDimension dim : deviceDimensions.getRequiredDimensionList()) {
      if (dim.getName().equals(dimensionName)) {
        values.add(dim.getValue());
      }
    }
    return values;
  }

  public int getScore() {
    if (score.isEmpty()) {
      List<DeviceRequirement> specs = spec.getDeviceRequirements().getDeviceRequirementList();
      score = Optional.of(computeScore(specs, 0, new HashSet<>(), new HashSet<>()));
    }
    return score.get();
  }

  private int computeScore(
      List<DeviceRequirement> specs,
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
    int searchDepth = min(2, candidates.size());
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

  public boolean hasMaxScore() {
    return getScore()
        == (MasterSingleDeviceAssessment.MAX_SCORE
            * spec.getDeviceRequirements().getDeviceRequirementCount());
  }

  public String getHostname() {
    return hostname;
  }

  public MasterSingleDeviceAssessment getOverallDeviceAssessment(DeviceRequirement spec) {
    return requirementsToOverallAssessments.get(spec);
  }

  public ImmutableList<DeviceCandidate> getTopCandidates(DeviceRequirement spec, int limit) {
    List<DeviceCandidate> candidates = requirementsToSortedCandidates.get(spec);
    if (candidates.size() > limit) {
      return ImmutableList.copyOf(candidates.subList(0, limit));
    }
    return ImmutableList.copyOf(candidates);
  }

  /** Candidate device for allocation. */
  @AutoValue
  public abstract static class DeviceCandidate {
    public abstract String id();

    public abstract MasterSingleDeviceAssessment assessment();

    public static DeviceCandidate create(String id, MasterSingleDeviceAssessment assessment) {
      return new AutoValue_MasterLabAssessment_DeviceCandidate(id, assessment);
    }
  }
}
