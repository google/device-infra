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

package com.google.devtools.mobileharness.infra.controller.test.local.utp.common;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.infra.controller.test.local.utp.proto.IncompatibleReasonProto.ConverterIncompatibleReason;
import com.google.devtools.mobileharness.infra.controller.test.local.utp.proto.IncompatibleReasonProto.ConverterInfraIncompatibleReason;
import com.google.devtools.mobileharness.infra.controller.test.local.utp.proto.IncompatibleReasonProto.IncompatibleReason;
import com.google.devtools.mobileharness.infra.controller.test.local.utp.proto.IncompatibleReasonProto.InfraIncompatibleReason;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/** Hybrid UTP mode incompatible reasons of a MH test. */
@ThreadSafe
public class IncompatibleReasons {

  private final Object lock = new Object();

  @GuardedBy("lock")
  private final List<IncompatibleReason> overallReasons = new ArrayList<>();

  /** Key is converter name prefix, value is not empty. */
  @GuardedBy("lock")
  private final Map<String, List<IncompatibleReason>> converterReasons = new HashMap<>();

  /** Adds a new MH infra-related hybrid UTP mode incompatible reason to the test. */
  public void addReason(InfraIncompatibleReason reason) {
    addReason(reason, null /* detail */);
  }

  /** Adds a new MH infra-related hybrid UTP mode incompatible reason to the test. */
  public void addReason(InfraIncompatibleReason reason, @Nullable String detail) {
    IncompatibleReason.Builder incompatibleReason =
        IncompatibleReason.newBuilder().setInfraCompatibleReason(reason);
    if (detail != null) {
      incompatibleReason.setDetail(detail);
    }
    addIncompatibleReasons(
        null /* converterNamePrefix */,
        ImmutableList.of(incompatibleReason.build()),
        true /* fatal */);
  }

  /**
   * Adds a new MH infra-related reason why a converter does not support hybrid UTP mode.
   *
   * @param fatal whether the reason is a fatal reason which affects the result whether the test
   *     runs in hybrid UTP mode, or just a reason which does not affect the test mode
   */
  public void addReason(
      String converterNamePrefix, ConverterInfraIncompatibleReason reason, boolean fatal) {
    addIncompatibleReasons(
        converterNamePrefix,
        ImmutableList.of(
            IncompatibleReason.newBuilder()
                .setConverterInfraIncompatibleReason(reason)
                .setConverterNamePrefix(converterNamePrefix)
                .build()),
        fatal);
  }

  /**
   * Adds reasons why a converter decides not to convert the test to hybrid UTP mode, e.g., the test
   * input specifies some features of the corresponding device/driver/decorator, which are not
   * supported by UTP test runner.
   *
   * @param fatal whether the reason is a fatal reason which affects the result whether the test
   *     runs in hybrid UTP mode, or just a reason which does not affect the test mode
   */
  public void addReasons(
      String converterNamePrefix, List<ConverterIncompatibleReason> reasons, boolean fatal) {
    addIncompatibleReasons(
        converterNamePrefix,
        reasons.stream()
            .map(
                reason ->
                    IncompatibleReason.newBuilder().setConverterIncompatibleReason(reason).build())
            .collect(toList()),
        fatal);
  }

  /** Adds all incompatible reasons to MH test properties. */
  public void addToTestProperties(Properties testProperties) {
    Map<String, String> result = new HashMap<>();
    synchronized (lock) {
      if (!overallReasons.isEmpty()) {
        result.put(
            Ascii.toLowerCase(PropertyName.Test.HYBRID_UTP_SUMMARY_INCOMPATIBLE_REASON.toString()),
            formatReasons(overallReasons, true /* withContext */));
      }

      converterReasons.forEach(
          (converterNamePrefix, reasons) ->
              result.put(
                  Ascii.toLowerCase(Test.PREFIX_HYBRID_UTP_DETAILED_INCOMPATIBLE_REASON)
                      + converterNamePrefix,
                  formatReasons(reasons, false /* withContext */)));
    }
    testProperties.addAll(result);
  }

  private void addIncompatibleReasons(
      @Nullable String converterNamePrefix, List<IncompatibleReason> reasons, boolean fatal) {
    synchronized (lock) {
      if (fatal) {
        overallReasons.addAll(reasons);
      }
      if (converterNamePrefix != null) {
        converterReasons
            .computeIfAbsent(converterNamePrefix, key -> new ArrayList<>())
            .addAll(reasons);
      }
    }
  }

  private static String formatReasons(List<IncompatibleReason> reasons, boolean withContext) {
    return reasons.stream()
        .map(reason -> formatReason(reason, withContext))
        .sorted()
        .distinct()
        .collect(joining(";"));
  }

  private static String formatReason(IncompatibleReason reason, boolean withContext) {
    String formattedReason;
    @Nullable String context = null;
    switch (reason.getReasonCase()) {
      case CONVERTER_INCOMPATIBLE_REASON:
        formattedReason = reason.getConverterIncompatibleReason().name();
        break;
      case INFRA_COMPATIBLE_REASON:
        formattedReason = reason.getInfraCompatibleReason().name();
        break;
      case CONVERTER_INFRA_INCOMPATIBLE_REASON:
        formattedReason = reason.getConverterInfraIncompatibleReason().name();
        context = reason.getConverterNamePrefix();
        break;
      default:
        formattedReason = "";
    }
    formattedReason = Ascii.toLowerCase(formattedReason);
    if (withContext && context != null) {
      formattedReason = String.format("%s(%s)", formattedReason, context);
    }
    if (!reason.getDetail().isEmpty()) {
      formattedReason = String.format("%s:%s", formattedReason, reason.getDetail());
    }
    return formattedReason;
  }
}
