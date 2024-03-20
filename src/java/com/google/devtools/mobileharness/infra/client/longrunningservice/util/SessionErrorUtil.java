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

package com.google.devtools.mobileharness.infra.client.longrunningservice.util;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.common.metrics.stability.converter.ErrorModelConverter.toDeserializedException;
import static java.util.stream.Collectors.partitioningBy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.devtools.common.metrics.stability.converter.DeserializedException;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginError;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Utility for parsing errors from session results. */
public class SessionErrorUtil {

  private SessionErrorUtil() {}

  /**
   * Returns the highest priority exception as the session error, and other exceptions as its
   * suppressed exceptions.
   *
   * <p>Priority: exceptions from the plugin with the specified plugin label -> the exception from
   * the session runner -> exceptions from other session plugins.
   */
  public static Optional<DeserializedException> getSessionError(
      SessionDetail sessionDetail, String pluginLabel) {
    Optional<DeserializedException> sessionRunnerError =
        sessionDetail.hasSessionRunnerError()
            ? Optional.of(toDeserializedException(sessionDetail.getSessionRunnerError()))
            : Optional.empty();

    // Selects exceptions by plugin labels.
    List<SessionPluginError> sessionPluginErrors =
        sessionDetail.getSessionOutput().getSessionPluginErrorList();
    Map<Boolean, List<SessionPluginError>> sessionPluginErrorsPartitionedByLabel =
        sessionPluginErrors.stream()
            .collect(
                partitioningBy(error -> error.getPluginLabel().getLabel().equals(pluginLabel)));
    ImmutableList<DeserializedException> selectedPluginErrors =
        sessionPluginErrorsPartitionedByLabel.get(true).stream()
            .map(selectedPluginError -> toDeserializedException(selectedPluginError.getError()))
            .collect(toImmutableList());
    ImmutableList<DeserializedException> otherSessionPluginErrors =
        sessionPluginErrorsPartitionedByLabel.get(false).stream()
            .map(
                otherSessionPluginError ->
                    toDeserializedException(otherSessionPluginError.getError()))
            .collect(toImmutableList());

    // Sorts session errors.
    ImmutableList<DeserializedException> sortedSessionErrors =
        Streams.concat(
                selectedPluginErrors.stream(),
                sessionRunnerError.stream(),
                otherSessionPluginErrors.stream())
            .collect(toImmutableList());
    if (sortedSessionErrors.isEmpty()) {
      return Optional.empty();
    }

    // Adds other errors as suppressed errors of the first one.
    DeserializedException result = sortedSessionErrors.get(0);
    sortedSessionErrors.stream().skip(1L).forEach(result::addSuppressed);

    return Optional.of(result);
  }
}
