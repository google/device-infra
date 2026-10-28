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

package com.google.wireless.qa.mobileharness.shared.api.driver;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_SMART_SHARD_TEST_NAMES;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.SHARD_COUNT;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.SHARD_INDEX;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidInstrumentationDriverSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Resolves Android instrumentation option maps, retry/sharding/repeated-test filters, test
 * properties, and spilled device target files prior to execution.
 */
final class AndroidInstrumentationOptionsPreparer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String ARGUMENT_TEST_SIZE = "size";
  private static final String ARGUMENT_NUM_SHARDS = "numShards";
  private static final String ARGUMENT_SHARD_INDEX = "shardIndex";

  private final AndroidInstrumentationTestFileSpiller testFileSpiller;

  @Inject
  AndroidInstrumentationOptionsPreparer(AndroidInstrumentationTestFileSpiller testFileSpiller) {
    this.testFileSpiller = testFileSpiller;
  }

  /**
   * Parses job instrumentation options, applies target/filter transformations, records option
   * properties onto {@code testInfo}, and spills oversized target lists to device files.
   */
  PreparedInstrumentationOptions prepare(
      TestInfo testInfo,
      String deviceId,
      int deviceSdkVersion,
      @Nullable String externalStoragePath)
      throws MobileHarnessException, InterruptedException {
    JobInfo job = testInfo.jobInfo();
    ImmutableList<Optional<Map<String, String>>> optionMaps = parseOptionMaps(job);

    String testTarget = resolveTestTarget(testInfo, optionMaps);

    if (isUniformSharding(testInfo)) {
      populateOptionMapsForUniformSharding(testInfo, optionMaps);
    }

    recordOptionsProperties(testInfo, optionMaps);

    testTarget =
        testFileSpiller.prepareTestFiles(
            testInfo, deviceId, deviceSdkVersion, externalStoragePath, optionMaps, testTarget);

    return PreparedInstrumentationOptions.create(optionMaps, testTarget);
  }

  /**
   * Parses {@code options} or sequential {@code options_<i>} parameters from {@code jobInfo}. The
   * returned list always contains at least one entry ({@link Optional#empty()} denotes an explicit
   * {@code SKIP} sequential entry).
   */
  private static ImmutableList<Optional<Map<String, String>>> parseOptionMaps(JobInfo jobInfo) {
    Map<String, String> singletonOptionMap =
        parseSingleOptionMap(jobInfo.params().get(AndroidInstrumentationDriverSpec.PARAM_OPTIONS));
    if (!singletonOptionMap.isEmpty()) {
      return ImmutableList.of(Optional.of(singletonOptionMap));
    }

    ImmutableList.Builder<Optional<Map<String, String>>> optionMaps = ImmutableList.builder();
    for (int i = 0; ; i++) {
      String paramValue =
          jobInfo.params().get(AndroidInstrumentationDriverSpec.PARAM_OPTIONS + "_" + i);
      if (paramValue == null) {
        break;
      }
      if (paramValue.equals(AndroidInstrumentationDriverSpec.SKIPPED_OPTION_STR)) {
        optionMaps.add(Optional.empty());
        continue;
      }
      Map<String, String> sequentialOptionMap = parseSingleOptionMap(paramValue);
      if (sequentialOptionMap.isEmpty()) {
        break;
      }
      optionMaps.add(Optional.of(sequentialOptionMap));
    }
    ImmutableList<Optional<Map<String, String>>> parsedOptionMaps = optionMaps.build();
    return parsedOptionMaps.isEmpty()
        ? ImmutableList.of(Optional.of(singletonOptionMap))
        : parsedOptionMaps;
  }

  private static Map<String, String> parseSingleOptionMap(@Nullable String rawOptions) {
    if (isNullOrEmpty(rawOptions)) {
      return new LinkedHashMap<>();
    }
    boolean hasEscapedComma = rawOptions.contains(AndroidInstrumentationDriverSpec.COMMA_ESCAPED);
    String normalized =
        hasEscapedComma
            ? rawOptions.replace(
                AndroidInstrumentationDriverSpec.COMMA_ESCAPED,
                AndroidInstrumentationDriverSpec.COMMA_ENCODED)
            : rawOptions;
    Map<String, String> parsed = new LinkedHashMap<>(StrUtil.toMap(normalized, true));
    if (hasEscapedComma) {
      parsed.replaceAll(
          (key, value) -> value.replace(AndroidInstrumentationDriverSpec.COMMA_ENCODED, ","));
    }
    return parsed;
  }

  @Nullable
  private static String resolveTestTarget(
      TestInfo testInfo, List<Optional<Map<String, String>>> optionMaps) {

    String testName = testInfo.locator().getName();
    if (Ascii.equalsIgnoreCase(testName, AndroidInstrumentationDriverSpec.TEST_NAME_ALL)) {
      testInfo.log().atInfo().alsoTo(logger).log("Running all tests");
      return null;
    }
    if (isUniformSharding(testInfo)) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Running %s tests with uniform sharding", testName);
      return null;
    }
    if (testInfo.properties().has(ANDROID_INSTRUMENTATION_SMART_SHARD_TEST_NAMES)) {
      return testInfo.properties().get(ANDROID_INSTRUMENTATION_SMART_SHARD_TEST_NAMES);
    }
    if (Ascii.equalsIgnoreCase(testName, AndroidInstrumentationDriverSpec.TEST_NAME_SMALL)
        || Ascii.equalsIgnoreCase(testName, AndroidInstrumentationDriverSpec.TEST_NAME_MEDIUM)
        || Ascii.equalsIgnoreCase(testName, AndroidInstrumentationDriverSpec.TEST_NAME_LARGE)) {
      String size = Ascii.toLowerCase(testName);
      forEachActiveOptionMap(optionMaps, optionMap -> optionMap.put(ARGUMENT_TEST_SIZE, size));
      testInfo.log().atInfo().alsoTo(logger).log("Running all %s tests", size);
      return null;
    }
    return testName;
  }

  private static boolean isUniformSharding(TestInfo testInfo) {
    return false;
  }

  private static void forEachActiveOptionMap(
      List<Optional<Map<String, String>>> optionMaps, Consumer<Map<String, String>> action) {
    for (Optional<Map<String, String>> maybeOptionMap : optionMaps) {
      maybeOptionMap.ifPresent(action);
    }
  }

  private static void populateOptionMapsForUniformSharding(
      TestInfo testInfo, List<Optional<Map<String, String>>> optionMaps) {
    String shardIndex = testInfo.properties().get(SHARD_INDEX);
    String shardCount = testInfo.properties().get(SHARD_COUNT);
    if (!isNullOrEmpty(shardIndex) && !isNullOrEmpty(shardCount)) {
      forEachActiveOptionMap(
          optionMaps,
          optionMap -> {
            optionMap.put(ARGUMENT_SHARD_INDEX, shardIndex);
            optionMap.put(ARGUMENT_NUM_SHARDS, shardCount);
          });
    }
  }

  private static void recordOptionsProperties(
      TestInfo testInfo, List<Optional<Map<String, String>>> optionMaps) {
    JobInfo job = testInfo.jobInfo();
    if (optionMaps.size() == 1
        && job.params().get(AndroidInstrumentationDriverSpec.PARAM_OPTIONS + "_0") == null) {
      testInfo
          .properties()
          .add(
              AndroidInstrumentationDriverSpec.PROPERTY_OPTIONS,
              StrUtil.DEFAULT_MAP_JOINER.join(optionMaps.get(0).orElse(ImmutableMap.of())));
    } else {
      testInfo
          .properties()
          .add(
              AndroidInstrumentationDriverSpec.PROPERTY_ITER_COUNT,
              String.valueOf(optionMaps.size()));
      int idx = 0;
      for (Optional<Map<String, String>> maybeOptionMap : optionMaps) {
        testInfo
            .properties()
            .add(
                AndroidInstrumentationDriverSpec.PROPERTY_OPTIONS + "_" + idx++,
                maybeOptionMap
                    .map(StrUtil.DEFAULT_MAP_JOINER::join)
                    .orElse(AndroidInstrumentationDriverSpec.SKIPPED_OPTION_STR));
      }
    }
  }

  /**
   * Prepared instrumentation option maps (where {@link Optional#empty()} represents an explicit
   * {@code SKIP} sequential entry) and resolved top-level test target.
   */
  record PreparedInstrumentationOptions(
      ImmutableList<Optional<ImmutableMap<String, String>>> optionMaps,
      @Nullable String testTarget) {
    static PreparedInstrumentationOptions create(
        List<? extends Optional<? extends Map<String, String>>> optionMaps,
        @Nullable String testTarget) {
      ImmutableList.Builder<Optional<ImmutableMap<String, String>>> frozen =
          ImmutableList.builderWithExpectedSize(optionMaps.size());
      for (Optional<? extends Map<String, String>> entry : optionMaps) {
        frozen.add(entry.map(ImmutableMap::copyOf));
      }
      return new PreparedInstrumentationOptions(frozen.build(), testTarget);
    }
  }
}
