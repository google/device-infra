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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Optional;

/** Args for creating a subplan. */
@AutoValue
public abstract class AddSubPlanArgs {

  /** The path to the xTS root directory. */
  public abstract Path xtsRootDir();

  /** The xts type. */
  public abstract String xtsType();

  /** The session index to derive from. */
  public abstract int sessionIndex();

  /** The name of the subplan to create. */
  public abstract Optional<String> subPlanName();

  /**
   * The result type to include for creating the subplan. Possible values include {@link
   * ResultType#PASSED}, {@link ResultType#FAILED}, {@link ResultType#NOT_EXECUTED}.
   */
  public abstract ImmutableSet<ResultType> resultTypes();

  /** Include filters passed in for the session. */
  public abstract ImmutableSet<String> passedInIncludeFilters();

  /** Exclude filters passed in for the session. */
  public abstract ImmutableSet<String> passedInExcludeFilters();

  /** The test module to run. */
  public abstract Optional<String> module();

  /** Whether the given module is for Non-Tradefed. False by default. */
  public abstract boolean isNonTradefedModule();

  /** The test to run, combined with the given module. Only works when module specified. */
  public abstract Optional<String> test();

  /** The abi to test, combined with the given module. Only works when module specified. */
  public abstract Optional<String> abi();

  public static Builder builder() {
    return new AutoValue_AddSubPlanArgs.Builder()
        .setResultTypes(ImmutableSet.of())
        .setPassedInIncludeFilters(ImmutableSet.of())
        .setPassedInExcludeFilters(ImmutableSet.of())
        .setIsNonTradefedModule(false);
  }

  /** Builder for {@link AddSubPlanArgs}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setXtsRootDir(Path xtsRootDir);

    public abstract Builder setXtsType(String xtsType);

    public abstract Builder setSessionIndex(int sessionIndex);

    public abstract Builder setSubPlanName(String subPlanName);

    public abstract Builder setResultTypes(ImmutableSet<ResultType> resultTypes);

    public abstract Builder setPassedInIncludeFilters(ImmutableSet<String> passedInIncludeFilters);

    public abstract Builder setPassedInExcludeFilters(ImmutableSet<String> passedInExcludeFilters);

    public abstract Builder setModule(String module);

    public abstract Builder setIsNonTradefedModule(boolean isNonTradefedModule);

    public abstract Builder setTest(String test);

    public abstract Builder setAbi(String abi);

    public abstract AddSubPlanArgs build();
  }
}
