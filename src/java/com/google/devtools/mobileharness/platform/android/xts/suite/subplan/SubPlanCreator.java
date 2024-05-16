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

package com.google.devtools.mobileharness.platform.android.xts.suite.subplan;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Arrays.stream;

import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Longs;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Attribute;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.xml.XmlConstants;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.ConfigurationUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteTestFilter;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.PreviousResultLoader;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Class for creating sub-plans. */
public class SubPlanCreator {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String XML_EXT = ".xml";

  private final PreviousResultLoader previousResultLoader;
  private final ConfigurationUtil configurationUtil;
  private final LocalFileUtil localFileUtil;

  @Inject
  SubPlanCreator(
      PreviousResultLoader previousResultLoader,
      LocalFileUtil localFileUtil,
      ConfigurationUtil configurationUtil) {
    this.previousResultLoader = previousResultLoader;
    this.localFileUtil = localFileUtil;
    this.configurationUtil = configurationUtil;
  }

  /**
   * Creates and serializes a subplan derived from a result.
   *
   * @return serialized subplan file.
   */
  public Optional<File> createAndSerializeSubPlan(AddSubPlanArgs addSubPlanArgs)
      throws MobileHarnessException {
    Path xtsRootDir = addSubPlanArgs.xtsRootDir();
    String xtsType = addSubPlanArgs.xtsType();
    int sessionIndex = addSubPlanArgs.sessionIndex();
    Optional<String> subPlanName = addSubPlanArgs.subPlanName();
    ImmutableSet<String> resultTypes = getResultTypes(addSubPlanArgs.resultTypes());

    Result previousResult =
        previousResultLoader.loadPreviousResult(
            XtsDirUtil.getXtsResultsDir(xtsRootDir, xtsType), sessionIndex);

    Optional<Attribute> startTimeAttr =
        previousResult.getAttributeList().stream()
            .filter(attr -> attr.getKey().equals(XmlConstants.START_TIME_ATTR))
            .findFirst();
    @Nullable
    Long startTimeMillis =
        startTimeAttr.isPresent() ? Longs.tryParse(startTimeAttr.get().getValue()) : null;

    File subPlanFile =
        getSubPlanFile(
            xtsRootDir,
            xtsType,
            subPlanName.orElse(null),
            sessionIndex,
            resultTypes,
            startTimeMillis == null ? 0L : startTimeMillis);

    List<String> includeFiltersFromPrevResult = previousResult.getIncludeFilterList();
    List<String> excludeFiltersFromPrevResult = previousResult.getExcludeFilterList();

    SubPlan subPlan =
        SubPlanHelper.createSubPlanForPreviousResult(
            previousResult,
            resultTypes,
            /* addSubPlanCmd= */ true,
            includeFiltersFromPrevResult.stream()
                .map(SuiteTestFilter::create)
                .collect(toImmutableSet()),
            excludeFiltersFromPrevResult.stream()
                .map(SuiteTestFilter::create)
                .collect(toImmutableSet()),
            /* passedInModules= */ ImmutableSet.of());

    if (!addSubPlanArgs.passedInIncludeFilters().isEmpty()
        || !addSubPlanArgs.passedInExcludeFilters().isEmpty()) {
      // Get exhaustive list of non tf modules in the xts test cases dir.
      ImmutableMap<String, Configuration> configsMap =
          configurationUtil.getConfigsV2FromDirs(
              ImmutableList.of(XtsDirUtil.getXtsTestCasesDir(xtsRootDir, xtsType).toFile()));
      ImmutableSet<String> allNonTfModules =
          configsMap.values().stream()
              .map(config -> config.getMetadata().getXtsModule())
              .collect(toImmutableSet());

      // Append the passed in filters to the subplan.
      SubPlanHelper.addPassedInFiltersToSubPlan(
          subPlan,
          addSubPlanArgs.passedInIncludeFilters().stream()
              .map(SuiteTestFilter::create)
              .collect(toImmutableSet()),
          addSubPlanArgs.passedInExcludeFilters().stream()
              .map(SuiteTestFilter::create)
              .collect(toImmutableSet()),
          allNonTfModules);
    }

    //  The given module (which may combine with abi and test) is added to subplan created based on
    // previous result
    String module = addSubPlanArgs.module().orElse("");
    if (!module.isEmpty()) {
      String moduleWithAbi =
          addSubPlanArgs.abi().isPresent()
              ? String.format("%s %s", addSubPlanArgs.abi().get(), module)
              : module;
      String test = addSubPlanArgs.test().orElse("");
      String includeEntry = moduleWithAbi + (isNullOrEmpty(test) ? "" : " " + test);
      if (addSubPlanArgs.isNonTradefedModule()) {
        subPlan.addNonTfIncludeFilter(includeEntry);
      } else {
        subPlan.addIncludeFilter(includeEntry);
      }
    }

    try {
      subPlan.serialize(
          new BufferedOutputStream(new FileOutputStream(subPlanFile)), /* tfFiltersOnly= */ false);
      logger.atInfo().log("Created subplan at %s", subPlanFile.getAbsolutePath());
      return Optional.of(subPlanFile);
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to create subplan file %s", subPlanFile.getAbsolutePath());
    }
    return Optional.empty();
  }

  private ImmutableSet<String> getResultTypes(ImmutableSet<ResultType> resultTypes) {
    if (resultTypes.isEmpty()) {
      return stream(ResultType.values())
          .map(resultType -> Ascii.toLowerCase(resultType.name()))
          .collect(toImmutableSet());
    }

    return resultTypes.stream()
        .map(resultType -> Ascii.toLowerCase(resultType.name()))
        .collect(toImmutableSet());
  }

  private File getSubPlanFile(
      Path xtsRootDir,
      String xtsType,
      @Nullable String subPlanName,
      int sessionIndex,
      ImmutableSet<String> resultTypes,
      long prevSessionStartTime)
      throws MobileHarnessException {
    Path subPlansDir = XtsDirUtil.getXtsSubPlansDir(xtsRootDir, xtsType);
    if (!localFileUtil.isDirExist(subPlansDir)) {
      localFileUtil.prepareDir(subPlansDir);
    }

    Path newSubPlanFile =
        subPlansDir.resolve(
            getSubPlanName(subPlanName, sessionIndex, resultTypes, prevSessionStartTime) + XML_EXT);
    if (localFileUtil.isFileExist(newSubPlanFile)) {
      throw new MobileHarnessException(
          ExtErrorId.SUBPLAN_CREATOR_SUBPLAN_FILE_ALREADY_EXISTED,
          String.format("Subplan file %s already existed", newSubPlanFile));
    }
    return newSubPlanFile.toFile();
  }

  private String getSubPlanName(
      @Nullable String subPlanName,
      int sessionIndex,
      ImmutableSet<String> resultTypes,
      long prevSessionStartTime) {
    return subPlanName == null
        ? createSubPlanName(sessionIndex, resultTypes, prevSessionStartTime)
        : subPlanName;
  }

  private String createSubPlanName(
      int sessionIndex, ImmutableSet<String> resultTypes, long prevSessionStartTime) {
    StringBuilder sb = new StringBuilder();
    Joiner.on("_").appendTo(sb, resultTypes);
    sb.append("_");
    sb.append(sessionIndex);
    sb.append("_");

    // use unique start time for name
    sb.append(XtsDirUtil.getDirSuffix(Instant.ofEpochMilli(prevSessionStartTime)));
    return sb.toString();
  }
}
