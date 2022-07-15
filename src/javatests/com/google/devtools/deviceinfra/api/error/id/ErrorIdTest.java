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

package com.google.devtools.deviceinfra.api.error.id;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;

import com.google.auto.value.AutoValue;
import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.common.base.Enums;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.google.common.truth.Correspondence;
import com.google.devtools.common.metrics.stability.util.ErrorIdFormatter;
import com.google.devtools.deviceinfra.api.error.DeviceInfraExceptionGenerator;
import com.google.devtools.deviceinfra.api.error.id.proto.ErrorCodeRangeProto.ErrorCodeRange;
import java.util.Collection;
import java.util.stream.Stream;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ErrorIdTest {

  private static final Converter<String, String> CLASS_SIMPLE_NAME_CASE_CONVERTER =
      CaseFormat.LOWER_CAMEL.converterTo(CaseFormat.UPPER_UNDERSCORE);
  private static final String MIN_CODE_SUFFIX = "_MIN_CODE";
  private static final String MAX_CODE_SUFFIX = "_MAX_CODE";

  private static final ImmutableList<String> PLATFORM_RELATED_ERROR_NAME_SUBSTRINGS =
      ImmutableList.of("ADB", "ANDROID", "FASTBOOT", "IOS", "MONSOON");
  private static final ImmutableSet<String> PLATFORM_UNRELATED_ERROR_ID_CLASS_SIMPLE_NAMES =
      ImmutableSet.of("BasicErrorId", "InfraErrorId");

  private static ImmutableMap<Class<DeviceInfraErrorId>, ImmutableList<DeviceInfraErrorId>>
      errorIds;

  @SuppressWarnings("unchecked")
  @BeforeClass
  public static void setUp() throws Exception {
    // Lists all top-level ErrorId enum classes in the "defined" package.
    errorIds =
        ClassPath.from(ErrorIdTest.class.getClassLoader())
            .getTopLevelClasses("com.google.devtools.deviceinfra.api.error.id.defined")
            .stream()
            .map(ClassInfo::load)
            .filter(DeviceInfraErrorId.class::isAssignableFrom)
            .filter(Class::isEnum)
            .map(clazz -> (Class<DeviceInfraErrorId>) clazz)
            .collect(
                toImmutableMap(
                    identity(), clazz -> ImmutableList.copyOf(clazz.getEnumConstants())));
  }

  @Test
  public void checkLoadingErrorIdClasses() throws Exception {
    assertThat(errorIds.keySet())
        .comparingElementsUsing(
            Correspondence.<Class<DeviceInfraErrorId>, String>transforming(
                Class::getSimpleName, "has a simple name of"))
        .containsAtLeast("BasicErrorId", "InfraErrorId");
  }

  @Test
  public void checkErrorCodeRanges() throws Exception {
    assertWithMessage("ErrorId whose code is out of range")
        .that(
            errorIds.entrySet().stream()
                .flatMap(
                    entry -> {
                      ErrorCodeRanges errorCodeRanges =
                          getErrorCodeRanges(entry.getKey().getSimpleName());
                      return entry.getValue().stream()
                          .filter(
                              errorId ->
                                  errorId.code() < errorCodeRanges.minCode().getNumber()
                                      || errorId.code() > errorCodeRanges.maxCode().getNumber())
                          .map(
                              errorId ->
                                  String.format(
                                      "%s.code() [%s] is out of %s code range [%s=%s, %s=%s]",
                                      debugString(errorId),
                                      errorId.code(),
                                      errorId.getClass().getSimpleName(),
                                      errorCodeRanges.minCode().name(),
                                      errorCodeRanges.minCode().getNumber(),
                                      errorCodeRanges.maxCode().name(),
                                      errorCodeRanges.maxCode().getNumber()));
                    })
                .collect(toImmutableList()))
        .isEmpty();
  }

  @Test
  public void checkToString() throws Exception {
    assertWithMessage(
            "ErrorId class which does not implement toString() by %s",
            ErrorIdFormatter.class.getSimpleName())
        .that(
            errorIds.entrySet().stream()
                .flatMap(
                    entry ->
                        entry.getValue().stream()
                            .filter(
                                errorId ->
                                    !ErrorIdFormatter.formatErrorId(errorId)
                                        .equals(errorId.toString()))
                            .findFirst()
                            .map(
                                errorId ->
                                    String.format(
                                        "%s (%s.toString() \"%s\" is not equal to \"%s\")",
                                        errorId.getClass().getSimpleName(),
                                        errorId.name(),
                                        errorId,
                                        ErrorIdFormatter.formatErrorId(errorId)))
                            .stream())
                .collect(toImmutableList()))
        .isEmpty();
  }

  @Test
  public void checkExceptionGenerator() throws Exception {
    assertWithMessage(
            "ErrorId class which does not implement %s",
            DeviceInfraExceptionGenerator.class.getSimpleName())
        .that(
            errorIds.keySet().stream()
                .filter(not(DeviceInfraExceptionGenerator.class::isAssignableFrom))
                .collect(toImmutableList()))
        .isEmpty();
  }

  @Test
  public void checkPlatformRelatedErrorIds() throws Exception {
    assertWithMessage(
            "Platform-related ErrorId (whose name() contains %s) defined in platform-independent"
                + " ErrorId class (%s) (they should be in platform-related ErrorId class like"
                + " AndroidErrorId/ExtErrorId)",
            PLATFORM_RELATED_ERROR_NAME_SUBSTRINGS, PLATFORM_UNRELATED_ERROR_ID_CLASS_SIMPLE_NAMES)
        .that(
            errorIds.entrySet().stream()
                .filter(
                    entry ->
                        PLATFORM_UNRELATED_ERROR_ID_CLASS_SIMPLE_NAMES.contains(
                            entry.getKey().getSimpleName()))
                .flatMap(entry -> entry.getValue().stream())
                .flatMap(
                    errorId -> {
                      ImmutableList<String> substrings =
                          PLATFORM_RELATED_ERROR_NAME_SUBSTRINGS.stream()
                              .filter(substring -> errorId.name().contains(substring))
                              .collect(toImmutableList());
                      return substrings.isEmpty()
                          ? Stream.empty()
                          : Stream.of(
                              String.format(
                                  "%s whose name() contains %s", debugString(errorId), substrings));
                    })
                .collect(toImmutableList()))
        .isEmpty();
  }

  @Test
  public void checkNonNull() throws Exception {
    assertWithMessage("ErrorId whose type() is null")
        .that(
            errorIds.values().stream()
                .flatMap(Collection::stream)
                .filter(errorId -> errorId.type() == null)
                .map(ErrorIdTest::debugString)
                .collect(toImmutableList()))
        .isEmpty();
  }

  @Test
  public void checkUniqueErrorCode() throws Exception {
    assertWithMessage("Error code used by multiple ErrorIds")
        .that(
            errorIds.values().stream()
                .flatMap(Collection::stream)
                .collect(
                    groupingBy(
                        DeviceInfraErrorId::code,
                        mapping(ErrorIdTest::debugString, toImmutableList())))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(
                    entry ->
                        String.format("Error code %s used by %s", entry.getKey(), entry.getValue()))
                .collect(toImmutableList()))
        .isEmpty();
  }

  @Test
  public void checkUniqueErrorName() throws Exception {
    assertWithMessage("Error name used by multiple ErrorIds")
        .that(
            errorIds.values().stream()
                .flatMap(Collection::stream)
                .collect(
                    groupingBy(
                        DeviceInfraErrorId::name,
                        mapping(ErrorIdTest::debugString, toImmutableList())))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(
                    entry ->
                        String.format("Error name %s used by %s", entry.getKey(), entry.getValue()))
                .collect(toImmutableList()))
        .isEmpty();
  }

  private static ErrorCodeRanges getErrorCodeRanges(String errorIdClassSimpleName) {
    String upperCaseClassSimpleName =
        CLASS_SIMPLE_NAME_CASE_CONVERTER.convert(errorIdClassSimpleName);
    return ErrorCodeRanges.of(
        getErrorCodeRange(errorIdClassSimpleName, upperCaseClassSimpleName, MIN_CODE_SUFFIX),
        getErrorCodeRange(errorIdClassSimpleName, upperCaseClassSimpleName, MAX_CODE_SUFFIX));
  }

  private static ErrorCodeRange getErrorCodeRange(
      String errorIdClassSimpleName, String upperCaseClassSimpleName, String suffix) {
    return Enums.getIfPresent(ErrorCodeRange.class, upperCaseClassSimpleName + suffix)
        .toJavaUtil()
        .orElseThrow(
            () ->
                new AssertionError(
                    String.format(
                        "%s should define %s%s and %s%s in ErrorCodeRange and check them in"
                            + " constructor",
                        errorIdClassSimpleName,
                        upperCaseClassSimpleName,
                        MIN_CODE_SUFFIX,
                        upperCaseClassSimpleName,
                        MAX_CODE_SUFFIX)));
  }

  private static String debugString(DeviceInfraErrorId errorId) {
    return String.format("%s.%s", errorId.getClass().getSimpleName(), errorId.name());
  }

  @AutoValue
  abstract static class ErrorCodeRanges {

    abstract ErrorCodeRange minCode();

    abstract ErrorCodeRange maxCode();

    private static ErrorCodeRanges of(ErrorCodeRange minCode, ErrorCodeRange maxCode) {
      return new AutoValue_ErrorIdTest_ErrorCodeRanges(minCode, maxCode);
    }
  }
}
