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

package com.google.devtools.deviceaction.common.utils;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ListMultimap;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.framework.proto.FileSpec;
import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/** A {@link Resolver} that applies different {@code SimpleResolver}s to resolve different files. */
class CompositeResolver implements Resolver {

  private final ImmutableList<SimpleResolver> resolvers;

  private CompositeResolver(Collection<SimpleResolver> resolvers) {
    this.resolvers = ImmutableList.copyOf(resolvers);
  }

  /** A builder class for {@link CompositeResolver}. */
  public static class Builder {
    private final LinkedHashSet<SimpleResolver> resolverSet = new LinkedHashSet<>();

    public Builder() {}

    public Builder addResolver(SimpleResolver resolver) {
      resolverSet.add(resolver);
      return this;
    }

    public CompositeResolver build() throws DeviceActionException {
      Conditions.checkState(
          !resolverSet.isEmpty(), ErrorType.INFRA_ISSUE, "Please provide resolver!");
      return new CompositeResolver(this.resolverSet);
    }
  }

  public static Builder toBuilder() {
    return new Builder();
  }

  /**
   * See {@link Resolver#resolve(List)}.
   *
   * <p>Resolves each file spec by an applied resolver. If no resolver applies to the file spec,
   * simply skip it.
   */
  @Override
  public ImmutableMultimap<String, File> resolve(List<FileSpec> fileSpecs)
      throws DeviceActionException, InterruptedException {
    ListMultimap<SimpleResolver, FileSpec> partition = ArrayListMultimap.create();
    for (FileSpec fileSpec : fileSpecs) {
      getFirstApplicableResolver(fileSpec).ifPresent(rr -> partition.put(rr, fileSpec));
    }
    ImmutableMultimap.Builder<String, File> builder = ImmutableListMultimap.builder();
    for (SimpleResolver resolver : partition.keySet()) {
      ImmutableMultimap<String, File> partial =
          resolver.resolve(ImmutableList.copyOf(partition.get(resolver)));
      builder.putAll(partial);
    }
    return builder.build();
  }

  private Optional<SimpleResolver> getFirstApplicableResolver(FileSpec fileSpec) {
    return resolvers.stream().filter(res -> res.appliesTo(fileSpec)).findFirst();
  }
}
