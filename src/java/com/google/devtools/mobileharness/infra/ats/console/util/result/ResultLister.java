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

package com.google.devtools.mobileharness.infra.ats.console.util.result;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Attribute;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.xml.XmlConstants;
import com.google.devtools.mobileharness.shared.util.base.TableFormatter;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.inject.Inject;

/** Lister for listing results. */
public class ResultLister {

  private final ResultListerHelper resultListerHelper;

  private final ConsoleInfo consoleInfo;

  @Inject
  ResultLister(ResultListerHelper resultListerHelper, ConsoleInfo consoleInfo) {
    this.resultListerHelper = resultListerHelper;
    this.consoleInfo = consoleInfo;
  }

  public String listResults(String xtsType) throws MobileHarnessException {
    // Lists all result dirs.
    String rootDir =
        consoleInfo
            .getXtsRootDirectory()
            .orElseThrow(() -> new IllegalStateException("XTS root directory not set"));
    String resultsDir = PathUtil.join(rootDir, String.format("android-%s", xtsType), "results");
    Map<Result, File> results = resultListerHelper.listResults(resultsDir);

    if (results.isEmpty()) {
      return "No results found";
    }

    List<List<String>> table = new ArrayList<>();
    // Adds header line.
    table.add(
        ImmutableList.of(
            "Session",
            "Pass",
            "Fail",
            "Modules Complete",
            "Result Directory",
            "Test Plan",
            "Device serial(s)",
            "Build ID",
            "Product"));

    int i = 0;
    for (Entry<Result, File> entry : results.entrySet()) {
      Result result = entry.getKey();
      File resultDir = entry.getValue();

      Map<String, ImmutableList<String>> attributes =
          result.getAttributeList().stream()
              .collect(
                  groupingBy(Attribute::getKey, mapping(Attribute::getValue, toImmutableList())));
      Map<String, ImmutableList<String>> buildInfoAttributes =
          result.getBuild().getAttributeList().stream()
              .collect(
                  groupingBy(Attribute::getKey, mapping(Attribute::getValue, toImmutableList())));

      table.add(
          ImmutableList.of(
              Integer.toString(i),
              Long.toString(result.getSummary().getPassed()),
              Long.toString(result.getSummary().getFailed()),
              String.format(
                  "%d of %d",
                  result.getSummary().getModulesDone(), result.getSummary().getModulesTotal()),
              resultDir.getName(),
              getAttribute(attributes, XmlConstants.SUITE_PLAN_ATTR),
              getAttribute(attributes, XmlConstants.DEVICES_ATTR).replace(",", ", "),
              getAttribute(buildInfoAttributes, "build_id"),
              getAttribute(buildInfoAttributes, "build_product")));
      i++;
    }

    return TableFormatter.displayTable(table);
  }

  private static String getAttribute(Map<String, ImmutableList<String>> attributes, String key) {
    ImmutableList<String> values = attributes.get(key);
    return values == null ? "unknown" : values.get(0);
  }
}
