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

import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.IMPORTANT;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.infra.ats.common.plan.PlanConfigUtil;
import com.google.devtools.mobileharness.infra.ats.common.plan.PlanConfigUtil.PlanConfigInfo;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.util.command.CommandHelper;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import javax.inject.Inject;

/** Helper to list or dump plans/configs. */
public class PlanHelper {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PlanConfigUtil planConfigUtil;
  private final ConsoleInfo consoleInfo;
  private final CommandHelper commandHelper;

  private final Supplier<ImmutableMap<String, PlanConfigInfo>> plansSupplier =
      Suppliers.memoize(this::doListPlans);

  @Inject
  PlanHelper(PlanConfigUtil planConfigUtil, ConsoleInfo consoleInfo, CommandHelper commandHelper) {
    this.planConfigUtil = planConfigUtil;
    this.consoleInfo = consoleInfo;
    this.commandHelper = commandHelper;
  }

  public ImmutableMap<String, PlanConfigInfo> listPlans() {
    return plansSupplier.get();
  }

  public String loadConfigContent(String configName) {
    ImmutableMap<String, PlanConfigInfo> configInfo = listPlans();
    if (!configInfo.containsKey(configName)) {
      logger.atWarning().with(IMPORTANCE, IMPORTANT).log("Config [%s] not found.", configName);
      return "";
    }
    PlanConfigInfo planConfigInfo = configInfo.get(configName);
    Optional<InputStream> content =
        planConfigUtil.getBundledConfigStream(planConfigInfo.source(), configName);
    if (content.isEmpty()) {
      logger
          .atWarning()
          .with(IMPORTANCE, IMPORTANT)
          .log("Failed to load config [%s] content.", configName);
      return "";
    }
    return convertPlanStreamToString(content.get());
  }

  private String convertPlanStreamToString(InputStream inputStream) {
    StringBuilder stringBuilder = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        stringBuilder.append(line).append("\n"); // Add newline for readability
      }
    } catch (Exception e) {
      logger
          .atWarning()
          .with(IMPORTANCE, IMPORTANT)
          .withCause(e)
          .log("Failed to convert config content to string");
      return "";
    }
    return stringBuilder.toString();
  }

  private ImmutableMap<String, PlanConfigInfo> doListPlans() {
    Path xtsRootDir = consoleInfo.getXtsRootDirectoryNonEmpty();
    Path toolsDir = XtsDirUtil.getXtsToolsDir(xtsRootDir, commandHelper.getXtsType());
    return planConfigUtil.loadAllConfigsInfo(toolsDir);
  }
}
