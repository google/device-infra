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

package com.google.devtools.mobileharness.infra.ats.common.plan;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.plan.TestPlanParser.TestPlanFilter;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.w3c.dom.Document;

@RunWith(JUnit4.class)
public final class TestPlanParserTest {
  private static final String TEST_DATA_PREFIX =
      "javatests/com/google/devtools/mobileharness/infra/ats/common/plan/testdata/testplan/";

  private static final String TEST_PLAN_A_XML =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + "test-plan-a.xml");
  private static final String TEST_PLAN_B_XML =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + "test-plan-b.xml");
  private static final String TEST_PLAN_C_XML =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + "test-plan-c.xml");
  private static final String TEST_PLAN_D_XML =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + "test-plan-d.xml");

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private PlanConfigUtil planConfigUtil;

  private TestPlanParser testPlanParser;

  @Before
  public void setUp() throws Exception {
    DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    Document testPlanA = documentBuilder.parse(new FileInputStream(TEST_PLAN_A_XML));
    Document testPlanB = documentBuilder.parse(new FileInputStream(TEST_PLAN_B_XML));
    Document testPlanC = documentBuilder.parse(new FileInputStream(TEST_PLAN_C_XML));
    Document testPlanD = documentBuilder.parse(new FileInputStream(TEST_PLAN_D_XML));

    when(planConfigUtil.loadConfig(eq("test-plan-a"), any())).thenReturn(Optional.of(testPlanA));
    when(planConfigUtil.loadConfig(eq("test-plan-b"), any())).thenReturn(Optional.of(testPlanB));
    when(planConfigUtil.loadConfig(eq("test-plan-c"), any())).thenReturn(Optional.of(testPlanC));
    when(planConfigUtil.loadConfig(eq("test-plan-d"), any())).thenReturn(Optional.of(testPlanD));

    testPlanParser = new TestPlanParser(planConfigUtil);
  }

  @Test
  public void parseFilters_success() throws Exception {
    TestPlanFilter filter = testPlanParser.parseFilters(Path.of("android-cts"), "test-plan-a");

    assertThat(filter.includeFilters())
        .containsExactly(
            "CtsAppSecurityHostTestCases"
                + " android.appsecurity.cts.ExternalStorageHostTest#testSystemGalleryExists",
            "CtsOsTestCases android.os.cts.StrictModeTest#testFileUriExposure",
            "CtsOsTestCases android.os.cts.BuildTest#testIsSecureUserBuild");
    assertThat(filter.excludeFilters())
        .containsExactly(
            "CtsNetTestCases android.net.cts.NetworkStatsManagerTest#testUidDetails",
            "CtsNetTestCases android.net.cts.NetworkStatsManagerTest#testUserSummary",
            "CtsContentSuggestionsTestCases");
    assertThat(filter.moduleMetadataIncludeFilters())
        .containsExactly("component", "cts-root", "component", "mcts-root", "mock_flag", "none");
    assertThat(filter.moduleMetadataExcludeFilters())
        .containsExactly(
            "component", "pts-root", "mock_key", "mock_value", "component", "gts-root");
    assertThat(filter.tests())
        .containsExactly(
            "com.android.compatibility.common.tradefed.testtype.suite.CompatibilityTestSuite");
  }

  @Test
  public void parseFilters_invalidPlan() throws Exception {
    assertThat(
            assertThrows(
                MobileHarnessException.class,
                () -> testPlanParser.parseFilters(Path.of("android-cts"), "test-plan-d")))
        .hasMessageThat()
        .isEqualTo(
            "Failed to parse test plan test-plan-e in test-plan-d since it is not a valid test"
                + " plan.");
    assertThat(
            assertThrows(
                MobileHarnessException.class,
                () -> testPlanParser.parseFilters(Path.of("android-cts"), "test-plan-e")))
        .hasMessageThat()
        .isEqualTo("Failed to parse test plan test-plan-e since it is not a valid test plan.");
  }

  @Test
  public void parseFilters_retry() throws Exception {
    assertThat(testPlanParser.parseFilters(Path.of("mock"), "cts", "retry"))
        .isEqualTo(
            TestPlanFilter.create(
                ImmutableSet.of(),
                ImmutableSet.of(),
                ImmutableMultimap.of(),
                ImmutableMultimap.of(),
                ImmutableSet.of()));
  }
}
