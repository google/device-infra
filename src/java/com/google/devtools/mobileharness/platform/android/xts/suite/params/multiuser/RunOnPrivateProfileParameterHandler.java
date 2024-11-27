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

package com.google.devtools.mobileharness.platform.android.xts.suite.params.multiuser;

import com.google.devtools.mobileharness.platform.android.xts.suite.params.IModuleParameterHandler;

/** Handler for {@link ModuleParameters#RUN_ON_PRIVATE_PROFILE}. */
public class RunOnPrivateProfileParameterHandler implements IModuleParameterHandler {

  @Override
  public String getParameterIdentifier() {
    return "run-on-private-profile";
  }
}
