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

package com.google.devtools.mobileharness.infra.ats.common;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.TestPlanLoader.TestPlanFilter;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.XtsType;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class TestPlanLoaderTest {
  private static final String TEST_DATA_PREFIX =
      "javatests/com/google/devtools/mobileharness/infra/ats/common/testdata/testplan/";

  private static final String TEST_PLAN_A_XML =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + "test-plan-a.xml");
  private static final String TEST_PLAN_B_XML =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + "test-plan-b.xml");
  private static final String TEST_PLAN_C_XML =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + "test-plan-c.xml");

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private JarFile xtsTradefedJarFile;
  @Mock private JarEntry entryA;
  @Mock private JarEntry entryB;
  @Mock private JarEntry entryC;

  @Test
  public void parseFilters_success() throws Exception {
    when(xtsTradefedJarFile.getJarEntry(eq("config/test-plan-a.xml"))).thenReturn(entryA);
    when(xtsTradefedJarFile.getJarEntry(eq("config/test-plan-b.xml"))).thenReturn(entryB);
    when(xtsTradefedJarFile.getJarEntry(eq("config/test-plan-c.xml"))).thenReturn(entryC);
    when(xtsTradefedJarFile.getInputStream(eq(entryA)))
        .thenReturn(new FileInputStream(TEST_PLAN_A_XML));
    when(xtsTradefedJarFile.getInputStream(eq(entryB)))
        .thenReturn(new FileInputStream(TEST_PLAN_B_XML));
    when(xtsTradefedJarFile.getInputStream(eq(entryC)))
        .thenReturn(new FileInputStream(TEST_PLAN_C_XML));

    TestPlanFilter filter = TestPlanLoader.parseFilters(xtsTradefedJarFile, "test-plan-a");
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
  }

  @Test
  public void parseFilters_invalidPlanName() throws Exception {
    when(xtsTradefedJarFile.getJarEntry(eq("invalid-plan-name"))).thenReturn(null);

    assertThrows(
        MobileHarnessException.class,
        () -> TestPlanLoader.parseFilters(xtsTradefedJarFile, "invalid-plan-name"));
  }

  @Test
  public void parseFilters_retry() throws Exception {
    assertThat(TestPlanLoader.parseFilters(Path.of("mock"), XtsType.CTS, "retry"))
        .isEqualTo(TestPlanFilter.create(ImmutableSet.of(), ImmutableSet.of()));
  }
}
