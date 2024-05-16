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

package com.google.devtools.mobileharness.infra.ats.console.util.plan;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.util.command.CommandHelper;
import com.google.devtools.mobileharness.infra.ats.console.util.plan.PlanConfigUtil.PlanConfigInfo;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import java.nio.file.Path;
import java.util.function.Supplier;
import javax.inject.Inject;

/** Lister for listing plans/configs. */
public class PlanLister {

  private final PlanConfigUtil planConfigUtil;
  private final ConsoleInfo consoleInfo;
  private final CommandHelper commandHelper;

  private final Supplier<ImmutableMap<String, PlanConfigInfo>> plansSupplier =
      Suppliers.memoize(this::doListPlans);

  @Inject
  PlanLister(PlanConfigUtil planConfigUtil, ConsoleInfo consoleInfo, CommandHelper commandHelper) {
    this.planConfigUtil = planConfigUtil;
    this.consoleInfo = consoleInfo;
    this.commandHelper = commandHelper;
  }

  public ImmutableMap<String, PlanConfigInfo> listPlans() {
    return plansSupplier.get();
  }

  private ImmutableMap<String, PlanConfigInfo> doListPlans() {
    String xtsRootDir = consoleInfo.getXtsRootDirectoryNonEmpty();
    Path toolsDir = XtsDirUtil.getXtsToolsDir(Path.of(xtsRootDir), commandHelper.getXtsType());
    return planConfigUtil.loadAllConfigs(toolsDir);
  }
}
