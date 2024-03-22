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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SubPlanTest {

  private static final String ABI = "armeabi-v7a";
  private static final String MODULE_A = "ModuleA";
  private static final String MODULE_B = "ModuleB";
  private static final String NON_TF_MODULE_C = "ModuleC";
  private static final String TEST_1 = "android.test.Foo#test1";
  private static final String TEST_2 = "android.test.Foo#test2";
  private static final String TEST_3 = "android.test.Foo#test3";

  private static final String FILTER_STRING_TEMPLATE = "%s %s %s";

  private static final String XML_ATTR = "%s=\"%s\"";

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void serialization() throws Exception {
    SubPlan subPlan = new SubPlan();
    subPlan.addIncludeFilter(String.format(FILTER_STRING_TEMPLATE, ABI, MODULE_A, TEST_1));
    Set<String> includeFilterSet = new HashSet<>();
    includeFilterSet.add(String.format(FILTER_STRING_TEMPLATE, ABI, MODULE_A, TEST_2));
    includeFilterSet.add(String.format(FILTER_STRING_TEMPLATE, ABI, MODULE_A, TEST_3));
    subPlan.addAllIncludeFilters(includeFilterSet); // add multiple include filters simultaneously
    subPlan.addIncludeFilter(MODULE_B);
    subPlan.addExcludeFilter(MODULE_B + " " + TEST_1);
    Set<String> excludeFilterSet = new HashSet<>();
    excludeFilterSet.add(MODULE_B + " " + TEST_2);
    excludeFilterSet.add(MODULE_B + " " + TEST_3);
    subPlan.addAllExcludeFilters(excludeFilterSet);

    subPlan.addNonTfIncludeFilter(
        String.format(FILTER_STRING_TEMPLATE, ABI, NON_TF_MODULE_C, TEST_1));
    subPlan.addNonTfExcludeFilter(
        String.format(FILTER_STRING_TEMPLATE, ABI, NON_TF_MODULE_C, TEST_2));

    File subPlanFile = temporaryFolder.newFile("test-subPlan-serialization.txt");
    try (OutputStream subPlanOutputStream = new FileOutputStream(subPlanFile)) {
      subPlan.serialize(subPlanOutputStream, /* tfFiltersOnly= */ false);
    }
    // Parse subPlan and assert correctness
    checkSubPlan(subPlanFile);
  }

  @Test
  public void parsing() throws Exception {
    File planFile = temporaryFolder.newFile("test-plan-parsing.txt");
    Writer writer = Files.newBufferedWriter(planFile.toPath(), UTF_8);
    try {
      Set<String> entries = new HashSet<>();
      entries.add(generateEntryXml(ABI, MODULE_A, TEST_1, true, false)); // include format 1
      entries.add(generateEntryXml(ABI, MODULE_A, TEST_2, true, false));
      entries.add(
          generateEntryXml(
              null,
              null,
              String.format(FILTER_STRING_TEMPLATE, ABI, MODULE_A, TEST_3),
              true,
              false)); // include format 2
      entries.add(generateEntryXml(null, MODULE_B, null, true, false));
      entries.add(generateEntryXml(null, null, MODULE_B + " " + TEST_1, false, false));
      entries.add(generateEntryXml(null, null, MODULE_B + " " + TEST_2, false, false));
      entries.add(generateEntryXml(null, null, MODULE_B + " " + TEST_3, false, false));

      entries.add(generateEntryXml(ABI, NON_TF_MODULE_C, TEST_1, true, true));
      entries.add(generateEntryXml(ABI, NON_TF_MODULE_C, TEST_2, false, true));

      String xml =
          String.format(
              "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                  + "<SubPlan version=\"2.0\">\n"
                  + "%s\n"
                  + "</SubPlan>",
              String.join("\n", entries));
      writer.write(xml);
      writer.flush();

      checkSubPlan(planFile);
    } finally {
      writer.close();
    }
  }

  private void checkSubPlan(File subPlanFile) throws Exception {
    InputStream subPlanInputStream = new FileInputStream(subPlanFile);
    SubPlan subPlan = new SubPlan();
    subPlan.parse(subPlanInputStream);

    SetMultimap<String, String> subPlanIncludeFiltersMultimap = subPlan.getIncludeFiltersMultimap();
    SetMultimap<String, String> subPlanExcludeFiltersMultimap = subPlan.getExcludeFiltersMultimap();

    assertThat(Multimaps.asMap(subPlanIncludeFiltersMultimap))
        .containsExactly(
            ABI + " " + MODULE_A,
            ImmutableSet.of(TEST_1, TEST_2, TEST_3),
            MODULE_B,
            ImmutableSet.of(SubPlan.ALL_TESTS_IN_MODULE));
    assertThat(Multimaps.asMap(subPlanExcludeFiltersMultimap))
        .containsExactly(MODULE_B, ImmutableSet.of(TEST_1, TEST_2, TEST_3));

    SetMultimap<String, String> subPlanNonTfIncludeFiltersMultimap =
        subPlan.getNonTfIncludeFiltersMultimap();
    SetMultimap<String, String> subPlanNonTfExcludeFiltersMultimap =
        subPlan.getNonTfExcludeFiltersMultimap();

    assertThat(Multimaps.asMap(subPlanNonTfIncludeFiltersMultimap))
        .containsExactly(ABI + " " + NON_TF_MODULE_C, ImmutableSet.of(TEST_1));
    assertThat(Multimaps.asMap(subPlanNonTfExcludeFiltersMultimap))
        .containsExactly(ABI + " " + NON_TF_MODULE_C, ImmutableSet.of(TEST_2));
  }

  // Helper for generating Entry XML tags
  private String generateEntryXml(
      String abi, String name, String filter, boolean include, boolean isNonTf) {
    String filterType = include ? "include" : "exclude";
    Set<String> attributes = new HashSet<>();
    if (filter != null) {
      attributes.add(String.format(XML_ATTR, filterType, filter));
    }
    if (name != null) {
      attributes.add(String.format(XML_ATTR, "name", name));
    }
    if (abi != null) {
      attributes.add(String.format(XML_ATTR, "abi", abi));
    }
    if (isNonTf) {
      attributes.add(String.format(XML_ATTR, "isNonTf", String.valueOf(isNonTf)));
    }
    return String.format("  <Entry %s/>\n", String.join(" ", attributes));
  }
}
