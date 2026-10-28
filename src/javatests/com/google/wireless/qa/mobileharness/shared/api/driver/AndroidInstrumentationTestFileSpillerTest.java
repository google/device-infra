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

package com.google.wireless.qa.mobileharness.shared.api.driver;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfoMocker;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AndroidInstrumentationTestFileSpillerTest {
  private static final String DEVICE_ID = "device_id";
  private static final int SDK_VERSION = 34;
  private static final String EXTERNAL_STORAGE = "/sdcard";

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Mock private AndroidInstrumentationUtil androidInstrumentationUtil;

  private final LocalFileUtil fileUtil = new LocalFileUtil();
  private final TestInfo testInfo = TestInfoMocker.mockTestInfo();
  private String tmpDir;
  private AndroidInstrumentationTestFileSpiller spiller;

  @Before
  public void setUp() throws Exception {
    tmpDir = tempFolder.newFolder("tmp").getAbsolutePath();
    when(testInfo.getTmpFileDir()).thenReturn(tmpDir);
    spiller = new AndroidInstrumentationTestFileSpiller(fileUtil, androidInstrumentationUtil);
  }

  @Test
  public void prepareTestFiles_atExactUtf8ByteLimitWithMultiByteChars_keepsInlineOptions()
      throws Exception {
    String topLevelTarget = "com.example.BoundaryTest#";
    String classOptionAtExactLimit = "a" + "é".repeat(32_755);
    Map<String, String> optionMap = new HashMap<>();
    optionMap.put("class", classOptionAtExactLimit);

    String resolvedTarget =
        prepareTestFiles(ImmutableList.of(Optional.of(optionMap)), topLevelTarget);

    assertThat(resolvedTarget).isEqualTo(topLevelTarget);
    assertThat(optionMap).containsExactly("class", classOptionAtExactLimit);
  }

  @Test
  public void prepareTestFiles_atExactUtf8ByteLimitWithMultiByteChars_doesNotPushTestFile()
      throws Exception {
    Map<String, String> optionMap = new HashMap<>();
    optionMap.put("class", "a" + "é".repeat(32_755));

    String unusedTarget =
        prepareTestFiles(ImmutableList.of(Optional.of(optionMap)), "com.example.BoundaryTest#");

    verify(androidInstrumentationUtil, never())
        .pushTestFile(any(), any(), anyInt(), any(), any(), any());
  }

  @Test
  public void prepareTestFiles_oneByteAboveUtf8LimitWithMultiByteChars_spillsPositiveTestFile()
      throws Exception {
    String topLevelTarget = "com.example.BoundaryTest#";
    String classOptionOneByteOver = "aa" + "é".repeat(32_755);
    Map<String, String> optionMap = new HashMap<>();
    optionMap.put("class", classOptionOneByteOver);
    String hostTestFile = PathUtil.join(tmpDir, "instrumentation_test_file.txt");
    when(androidInstrumentationUtil.pushTestFile(
            testInfo,
            DEVICE_ID,
            SDK_VERSION,
            hostTestFile,
            "instrumentation_test_file.txt",
            EXTERNAL_STORAGE))
        .thenReturn("/data/local/tmp/ajur/instrumentation_test_file.txt");

    String resolvedTarget =
        prepareTestFiles(ImmutableList.of(Optional.of(optionMap)), topLevelTarget);

    assertThat(resolvedTarget).isNull();
    assertThat(optionMap)
        .containsExactly("testFile", "/data/local/tmp/ajur/instrumentation_test_file.txt");
    assertThat(fileUtil.readFile(hostTestFile))
        .isEqualTo(topLevelTarget + "\n" + classOptionOneByteOver);
  }

  @Test
  public void prepareTestFiles_oversizedClassAndPackage_spillsPositiveTestFile() throws Exception {
    String largeClassTarget = "com.example.LargeTest#" + "a".repeat(40_000);
    String largePackageTarget = "com.example.pkg." + "b".repeat(30_000);
    Map<String, String> optionMap = new HashMap<>();
    optionMap.put("class", largeClassTarget);
    optionMap.put("package", largePackageTarget);
    String hostTestFile = PathUtil.join(tmpDir, "instrumentation_test_file.txt");
    when(androidInstrumentationUtil.pushTestFile(
            testInfo,
            DEVICE_ID,
            SDK_VERSION,
            hostTestFile,
            "instrumentation_test_file.txt",
            EXTERNAL_STORAGE))
        .thenReturn("/data/local/tmp/ajur/instrumentation_test_file.txt");

    String resolvedTarget =
        prepareTestFiles(ImmutableList.of(Optional.of(optionMap)), "com.example.TopLevelTest");

    assertThat(resolvedTarget).isNull();
    assertThat(optionMap)
        .containsExactly("testFile", "/data/local/tmp/ajur/instrumentation_test_file.txt");
    assertThat(fileUtil.readFile(hostTestFile))
        .isEqualTo("com.example.TopLevelTest\n" + largeClassTarget + "\n" + largePackageTarget);
  }

  @Test
  public void prepareTestFiles_oversizedNotClassAndNotPackage_spillsNegativeTestFile()
      throws Exception {
    String largeNotClass = "com.example.ExcludeTest#" + "c".repeat(40_000);
    String largeNotPackage = "com.example.excluded." + "d".repeat(30_000);
    Map<String, String> optionMap = new HashMap<>();
    optionMap.put("notClass", largeNotClass);
    optionMap.put("notPackage", largeNotPackage);
    String hostNotTestFile = PathUtil.join(tmpDir, "instrumentation_not_test_file.txt");
    when(androidInstrumentationUtil.pushTestFile(
            testInfo,
            DEVICE_ID,
            SDK_VERSION,
            hostNotTestFile,
            "instrumentation_not_test_file.txt",
            EXTERNAL_STORAGE))
        .thenReturn("/data/local/tmp/ajur/instrumentation_not_test_file.txt");

    String resolvedTarget =
        prepareTestFiles(ImmutableList.of(Optional.of(optionMap)), "com.example.KeepTargetTest");

    assertThat(resolvedTarget).isEqualTo("com.example.KeepTargetTest");
    assertThat(optionMap)
        .containsExactly("notTestFile", "/data/local/tmp/ajur/instrumentation_not_test_file.txt");
    assertThat(fileUtil.readFile(hostNotTestFile))
        .isEqualTo(largeNotClass + "\n" + largeNotPackage);
  }

  @Test
  public void prepareTestFiles_existingTestFileSpecified_keepsExistingTestFile() throws Exception {
    String oversizedClass = "com.example.BigTest#" + "x".repeat(70_000);
    Map<String, String> optionMap = new HashMap<>();
    optionMap.put("class", oversizedClass);
    optionMap.put("testFile", "/sdcard/preexisting_test_file.txt");

    String resolvedTarget =
        prepareTestFiles(ImmutableList.of(Optional.of(optionMap)), "com.example.KeepTargetTest");

    assertThat(resolvedTarget).isEqualTo("com.example.KeepTargetTest");
    assertThat(optionMap)
        .containsExactly("class", oversizedClass, "testFile", "/sdcard/preexisting_test_file.txt");
  }

  @Test
  public void prepareTestFiles_existingTestFileSpecified_doesNotPushTestFile() throws Exception {
    Map<String, String> optionMap = new HashMap<>();
    optionMap.put("class", "com.example.BigTest#" + "x".repeat(70_000));
    optionMap.put("testFile", "/sdcard/preexisting_test_file.txt");

    String unusedTarget =
        prepareTestFiles(ImmutableList.of(Optional.of(optionMap)), "com.example.KeepTargetTest");

    verify(androidInstrumentationUtil, never())
        .pushTestFile(any(), any(), anyInt(), any(), any(), any());
  }

  @Test
  public void prepareTestFiles_multiMapMixedSpillAndSkipped_foldsTopTargetIntoUnspilledActiveMap()
      throws Exception {
    String oversizedClass = "com.example.Spilled#" + "m".repeat(70_000);
    Map<String, String> spilledMap = new HashMap<>();
    spilledMap.put("class", oversizedClass);
    Map<String, String> smallMap = new HashMap<>();
    smallMap.put("class", "com.example.SmallTest");
    ImmutableList<Optional<Map<String, String>>> optionMaps =
        ImmutableList.of(Optional.of(spilledMap), Optional.empty(), Optional.of(smallMap));
    when(androidInstrumentationUtil.pushTestFile(
            testInfo,
            DEVICE_ID,
            SDK_VERSION,
            PathUtil.join(tmpDir, "instrumentation_test_file_0.txt"),
            "instrumentation_test_file_0.txt",
            EXTERNAL_STORAGE))
        .thenReturn("/data/local/tmp/ajur/instrumentation_test_file_0.txt");

    String resolvedTarget = prepareTestFiles(optionMaps, "com.example.SharedTarget");

    assertThat(resolvedTarget).isNull();
    assertThat(spilledMap)
        .containsExactly("testFile", "/data/local/tmp/ajur/instrumentation_test_file_0.txt");
    assertThat(smallMap).containsExactly("class", "com.example.SharedTarget,com.example.SmallTest");
  }

  @Test
  public void prepareTestFiles_multiMapPartialSpillWithBlankTopTarget_keepsUnspilledClassUnchanged()
      throws Exception {
    Map<String, String> spilledMap = new HashMap<>();
    spilledMap.put("class", "com.example.Spilled#" + "m".repeat(70_000));
    Map<String, String> smallMap = new HashMap<>();
    smallMap.put("class", "com.example.SmallTest");
    when(androidInstrumentationUtil.pushTestFile(
            eq(testInfo), eq(DEVICE_ID), eq(SDK_VERSION), any(), any(), eq(EXTERNAL_STORAGE)))
        .thenReturn("/data/local/tmp/ajur/instrumentation_test_file_0.txt");

    String resolvedTarget =
        prepareTestFiles(ImmutableList.of(Optional.of(spilledMap), Optional.of(smallMap)), "   ");

    assertThat(resolvedTarget).isNull();
    assertThat(smallMap).containsExactly("class", "com.example.SmallTest");
  }

  @Test
  public void prepareTestFiles_multiMapPartialSpillWhenUnspilledClassIsBlank_replacesBlankClass()
      throws Exception {
    Map<String, String> spilledMap = new HashMap<>();
    spilledMap.put("class", "com.example.Spilled#" + "m".repeat(70_000));
    Map<String, String> blankClassMap = new HashMap<>();
    blankClassMap.put("class", "   ");
    when(androidInstrumentationUtil.pushTestFile(
            eq(testInfo), eq(DEVICE_ID), eq(SDK_VERSION), any(), any(), eq(EXTERNAL_STORAGE)))
        .thenReturn("/data/local/tmp/ajur/instrumentation_test_file_0.txt");

    String resolvedTarget =
        prepareTestFiles(
            ImmutableList.of(Optional.of(spilledMap), Optional.of(blankClassMap)),
            "com.example.SharedTarget");

    assertThat(resolvedTarget).isNull();
    assertThat(blankClassMap).containsExactly("class", "com.example.SharedTarget");
  }

  @Test
  public void prepareTestFiles_parameterizedNestedAndUnclosedBrackets_splitsOnlyTopLevelCommas()
      throws Exception {
    String complexTargets =
        "com.example.ParamTest#testList[[1, 2], 3], "
            + "com.example.ParenTest#testTuple((a, b), c), , "
            + "com.example.UnclosedTest#testBracket[unclosed, "
            + "com.example.UnclosedParenTest#testParen(unclosed, "
            + "com.example.PaddingTest#"
            + "p".repeat(70_000);
    Map<String, String> optionMap = new HashMap<>();
    optionMap.put("class", complexTargets);
    when(androidInstrumentationUtil.pushTestFile(
            eq(testInfo), eq(DEVICE_ID), eq(SDK_VERSION), any(), any(), eq(EXTERNAL_STORAGE)))
        .thenReturn("/data/local/tmp/ajur/instrumentation_test_file.txt");

    String resolvedTarget = prepareTestFiles(ImmutableList.of(Optional.of(optionMap)), "");

    assertThat(resolvedTarget).isNull();
    assertThat(fileUtil.readFile(PathUtil.join(tmpDir, "instrumentation_test_file.txt")))
        .isEqualTo(
            String.join(
                "\n",
                "com.example.ParamTest#testList[[1, 2], 3]",
                "com.example.ParenTest#testTuple((a, b), c)",
                "com.example.UnclosedTest#testBracket[unclosed",
                "com.example.UnclosedParenTest#testParen(unclosed",
                "com.example.PaddingTest#" + "p".repeat(70_000)));
  }

  @Test
  public void prepareTestFiles_hostWriteFails_throwsWriteFileError() throws Exception {
    tempFolder.newFolder("tmp", "instrumentation_test_file.txt");
    Map<String, String> optionMap = new HashMap<>();
    optionMap.put("class", "com.example.Large#" + "z".repeat(70_000));

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class,
            () -> prepareTestFiles(ImmutableList.of(Optional.of(optionMap)), null));

    assertThat(thrown.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_INSTRUMENTATION_WRITE_FILE_ERROR);
  }

  @Nullable
  private String prepareTestFiles(
      List<Optional<Map<String, String>>> optionMaps, @Nullable String testTarget)
      throws MobileHarnessException, InterruptedException {
    return spiller.prepareTestFiles(
        testInfo, DEVICE_ID, SDK_VERSION, EXTERNAL_STORAGE, optionMaps, testTarget);
  }
}
