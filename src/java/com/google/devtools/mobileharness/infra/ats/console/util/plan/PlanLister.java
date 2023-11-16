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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Map.Entry.comparingByKey;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.util.plan.PlanConfigUtil.PlanConfigInfo;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import javax.inject.Inject;

/** Lister for listing plans/configs. */
public class PlanLister {

  private final PlanConfigUtil planConfigUtil;
  private final ConsoleInfo consoleInfo;

  @Inject
  PlanLister(PlanConfigUtil planConfigUtil, ConsoleInfo consoleInfo) {
    this.planConfigUtil = planConfigUtil;
    this.consoleInfo = consoleInfo;
  }

  public String listPlans() throws MobileHarnessException {
    String rootDir =
        consoleInfo
            .getXtsRootDirectory()
            .orElseThrow(() -> new IllegalStateException("XTS root directory not set"));

    Path toolsDir = Paths.get(rootDir).resolve("android-cts/tools");
    StringBuilder result = new StringBuilder("Available plans include:\n  ");

    Map<String, PlanConfigInfo> configNameToPlanConfigInfo =
        planConfigUtil.loadAllConfigs(toolsDir, /* ignoreException= */ true);

    ImmutableList<String> plans =
        configNameToPlanConfigInfo.entrySet().stream()
            .sorted(comparingByKey())
            .map(e -> String.format("%s: %s", e.getKey(), e.getValue().description()))
            .collect(toImmutableList());

    return Joiner.on("\n  ").appendTo(result, plans).toString();
  }
}
