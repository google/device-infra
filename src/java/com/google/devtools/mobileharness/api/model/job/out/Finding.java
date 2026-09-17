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

package com.google.devtools.mobileharness.api.model.job.out;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.devtools.common.metrics.stability.model.MetadataProvider;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionSummary;
import com.google.devtools.mobileharness.api.model.proto.Diagnostic;
import com.google.devtools.mobileharness.api.model.proto.Diagnostic.Finding.Severity;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A finding is a piece of information about an execution that is considered noteworthy.
 *
 * <p>TODO: This class doesn't integrate with {@code TouchableTiming}. We should either integrate
 * with it or remove TouchableTiming(preferred).
 */
public class Finding implements MetadataProvider {

  private final ConcurrentMap<String, String> metadata = new ConcurrentHashMap<>();
  private final ExceptionDetail exceptionDetailWithoutMetadata;
  private final Severity severity;

  @VisibleForTesting
  Finding(Severity severity, ExceptionDetail exceptionDetail) {
    this.severity = severity;
    if (exceptionDetail.getSummary().getMetadataCount() > 0) {
      // If duplicate keys exist, the last one wins.
      for (ExceptionSummary.MetadataEntry entry : exceptionDetail.getSummary().getMetadataList()) {
        metadata.put(entry.getKey(), entry.getValue());
      }

      ExceptionDetail.Builder exceptionDetailBuilder = exceptionDetail.toBuilder();
      exceptionDetailBuilder.getSummaryBuilder().clearMetadata();
      exceptionDetailWithoutMetadata = exceptionDetailBuilder.build();
    } else {
      exceptionDetailWithoutMetadata = exceptionDetail;
    }
  }

  @VisibleForTesting
  Finding(Diagnostic.Finding findingProto) {
    this(findingProto.getSeverity(), findingProto.getDetail());
  }

  public ExceptionDetail getDetail() {
    if (metadata.isEmpty()) {
      return exceptionDetailWithoutMetadata;
    }
    ExceptionDetail.Builder exceptionDetailBuilder = exceptionDetailWithoutMetadata.toBuilder();
    ExceptionSummary.Builder summaryBuilder = exceptionDetailBuilder.getSummaryBuilder();
    // Sorts by key so that the resulting proto is deterministic, since equals()/hashCode() compare
    // the proto and repeated fields are order-sensitive.
    ImmutableSortedMap.copyOf(metadata)
        .forEach(
            (key, value) ->
                summaryBuilder.addMetadata(
                    ExceptionSummary.MetadataEntry.newBuilder().setKey(key).setValue(value)));
    return exceptionDetailBuilder.build();
  }

  public Severity getSeverity() {
    return severity;
  }

  @Override
  public void addMetadata(String key, String value) {
    metadata.put(checkNotNull(key, "key"), checkNotNull(value, "value"));
  }

  @Override
  public ImmutableMap<String, String> getMetadata() {
    return ImmutableMap.copyOf(metadata);
  }

  @Override
  public Optional<String> getMetadata(String key) {
    return Optional.ofNullable(metadata.get(key));
  }

  public Diagnostic.Finding toProto() {
    return Diagnostic.Finding.newBuilder().setSeverity(severity).setDetail(getDetail()).build();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Finding that)) {
      return false;
    }
    return toProto().equals(that.toProto());
  }

  @Override
  public int hashCode() {
    return toProto().hashCode();
  }

  @Override
  public String toString() {
    return toProto().toString();
  }
}
