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

package com.google.devtools.mobileharness.platform.android.xts.suite.params;

import static com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters.ALL_FOLDABLE_STATES;
import static com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters.INSTANT_APP;
import static com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters.MULTIUSER;
import static com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters.MULTI_ABI;
import static com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters.NOT_INSTANT_APP;
import static com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters.NOT_MULTI_ABI;
import static com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters.NOT_RUN_ON_SDK_SANDBOX;
import static com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters.NOT_SECONDARY_USER;
import static com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters.NOT_SECONDARY_USER_ON_DEFAULT_DISPLAY;
import static com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters.NOT_SECONDARY_USER_ON_SECONDARY_DISPLAY;
import static com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters.NO_FOLDABLE_STATES;
import static com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters.RUN_ON_CLONE_PROFILE;
import static com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters.RUN_ON_SDK_SANDBOX;
import static com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters.RUN_ON_SECONDARY_USER;
import static com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters.RUN_ON_WORK_PROFILE;
import static com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters.SECONDARY_USER;
import static com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters.SECONDARY_USER_ON_DEFAULT_DISPLAY;
import static com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters.SECONDARY_USER_ON_SECONDARY_DISPLAY;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.platform.android.xts.suite.params.multiuser.RunOnCloneProfileParameterHandler;
import com.google.devtools.mobileharness.platform.android.xts.suite.params.multiuser.RunOnSecondaryUserParameterHandler;
import com.google.devtools.mobileharness.platform.android.xts.suite.params.multiuser.RunOnWorkProfileParameterHandler;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Helper to get the {@link IModuleParameterHandler} associated with the parameter. */
public final class ModuleParametersHelper {

  private static final ImmutableMap<ModuleParameters, IModuleParameterHandler> HANDLER_MAP =
      ImmutableMap.of(
          INSTANT_APP, new InstantAppHandler(),
          NOT_INSTANT_APP, new NegativeHandler(),
          // line separator
          MULTI_ABI, new NegativeHandler(),
          NOT_MULTI_ABI, new NotMultiAbiHandler(),
          // line separator
          RUN_ON_WORK_PROFILE, new RunOnWorkProfileParameterHandler(),
          RUN_ON_SECONDARY_USER, new RunOnSecondaryUserParameterHandler(),
          // line separator
          NO_FOLDABLE_STATES, new NegativeHandler(),
          ALL_FOLDABLE_STATES, new FoldableExpandingHandler(),
          RUN_ON_CLONE_PROFILE, new RunOnCloneProfileParameterHandler());

  private static final ImmutableMap<ModuleParameters, ImmutableSet<ModuleParameters>> GROUP_MAP =
      ImmutableMap.of(
          MULTIUSER,
          ImmutableSet.of(RUN_ON_CLONE_PROFILE, RUN_ON_SECONDARY_USER, RUN_ON_WORK_PROFILE));

  /**
   * Optional parameters are params that will not automatically be created when the module
   * parameterization is enabled. They will need to be explicitly enabled. They represent a second
   * set of parameterization that is less commonly requested to run. They could be upgraded to main
   * parameters in the future by moving them above.
   */
  private static final ImmutableMap<ModuleParameters, IModuleParameterHandler>
      OPTIONAL_HANDLER_MAP =
          ImmutableMap.of(
              SECONDARY_USER,
              new SecondaryUserHandler(),
              NOT_SECONDARY_USER,
              new NegativeHandler(),
              SECONDARY_USER_ON_SECONDARY_DISPLAY,
              new SecondaryUserOnSecondaryDisplayHandler(),
              NOT_SECONDARY_USER_ON_SECONDARY_DISPLAY,
              new NegativeHandler(),
              SECONDARY_USER_ON_DEFAULT_DISPLAY,
              new SecondaryUserOnDefaultDisplayHandler(),
              NOT_SECONDARY_USER_ON_DEFAULT_DISPLAY,
              new NegativeHandler(),
              RUN_ON_SDK_SANDBOX,
              new RunOnSdkSandboxHandler(),
              NOT_RUN_ON_SDK_SANDBOX,
              new NegativeHandler());

  // NOTE: OPTIONAL_GROUP_MAP is currently empty, but used on resolveParam(), so don't remove it
  private static final ImmutableMap<ModuleParameters, Set<ModuleParameters>> OPTIONAL_GROUP_MAP =
      ImmutableMap.of();

  /**
   * Gets the all {@link ModuleParameters} which are sub-params of a given {@link ModuleParameters}.
   *
   * <p>This will recursively resolve sub-groups and will only return {@link ModuleParameters} which
   * are not groups.
   *
   * <p>If {@code param} is not a group then a singleton set containing {@code param} will be
   * returned itself, regardless of {@code withOptional}.
   *
   * @param withOptional Whether or not to also check optional param groups.
   */
  public static ImmutableMap<ModuleParameters, IModuleParameterHandler> resolveParam(
      ModuleParameters param, boolean withOptional) {
    Set<ModuleParameters> mappedParams = GROUP_MAP.get(param);
    if (mappedParams == null && withOptional) {
      mappedParams = OPTIONAL_GROUP_MAP.get(param);
    }
    if (mappedParams == null) {
      IModuleParameterHandler handler = getParameterHandler(param, withOptional);
      if (handler == null) {
        // If the handler is not supported yet (for example, optional params) skip the param.
        return ImmutableMap.of();
      }
      return ImmutableMap.of(param, getParameterHandler(param, withOptional));
    }
    // If the parameter is a group, expand it.
    Map<ModuleParameters, IModuleParameterHandler> resolvedParams = new LinkedHashMap<>();
    for (ModuleParameters moduleParameters : mappedParams) {
      resolvedParams.put(moduleParameters, HANDLER_MAP.get(moduleParameters));
    }
    return ImmutableMap.copyOf(resolvedParams);
  }

  /**
   * Gets the {@link IModuleParameterHandler} associated with the requested parameter.
   *
   * @param withOptional Whether or not to also check optional params.
   */
  private static IModuleParameterHandler getParameterHandler(
      ModuleParameters param, boolean withOptional) {
    IModuleParameterHandler value = HANDLER_MAP.get(param);
    if (value == null && withOptional) {
      return OPTIONAL_HANDLER_MAP.get(param);
    }
    return value;
  }

  private ModuleParametersHelper() {}
}
