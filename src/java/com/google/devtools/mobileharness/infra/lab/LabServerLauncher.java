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

package com.google.devtools.mobileharness.infra.lab;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.EventBus;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.logging.flogger.FloggerFormatter;
import com.google.inject.Module;

/** Launcher of lab server. */
public class LabServerLauncher extends BaseLabServerLauncher {

  static {
    FloggerFormatter.initialize();
  }

  public static void main(String[] args) throws InterruptedException {
    new LabServerLauncher().run(args);
  }

  @Override
  protected ImmutableList<Module> createModules(
      ImmutableList<String> mainArgs,
      ImmutableMap<String, String> systemProperties,
      EventBus globalInternalBus)
      throws MobileHarnessException {
    ImmutableList.Builder<Module> modules = ImmutableList.builder();
    modules.add(
        new LabServerModule(mainArgs, System.getenv(), systemProperties, globalInternalBus));
    return modules.build();
  }
}
