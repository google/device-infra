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

package com.google.devtools.mobileharness.platform.android.xts.suite.screenshots;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.mobileharness.platform.android.xts.suite.screenshots.ScreenshotsMetadataUtil.CASE_TAG;
import static com.google.devtools.mobileharness.platform.android.xts.suite.screenshots.ScreenshotsMetadataUtil.DEFAULT_MODULE_NAME;
import static com.google.devtools.mobileharness.platform.android.xts.suite.screenshots.ScreenshotsMetadataUtil.ENCODING;
import static com.google.devtools.mobileharness.platform.android.xts.suite.screenshots.ScreenshotsMetadataUtil.MODULE_TAG;
import static com.google.devtools.mobileharness.platform.android.xts.suite.screenshots.ScreenshotsMetadataUtil.NS;
import static com.google.devtools.mobileharness.platform.android.xts.suite.screenshots.ScreenshotsMetadataUtil.SCREENSHOTS_DIR_NAME;
import static com.google.devtools.mobileharness.platform.android.xts.suite.screenshots.ScreenshotsMetadataUtil.SCREENSHOT_TAG;
import static com.google.devtools.mobileharness.platform.android.xts.suite.screenshots.ScreenshotsMetadataUtil.TEST_TAG;

import com.google.devtools.mobileharness.platform.android.xts.suite.screenshots.ScreenshotsMetadataUtil.ScreenshotTagData;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.inject.Guice;
import java.io.File;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

@RunWith(JUnit4.class)
public final class ScreenshotsMetadataUtilTest {

  private static final String INTERACTIVE_STEP_1 = "VerifyAppMenuStep";
  private static final String INTERACTIVE_STEP_2 = "VerifyScreenshot";
  private static final String SCREENSHOT_SUFFIX = "123456789.png";
  private static final String TEST_CLASS_1 = "com.google.android.gts.SampleTest";
  private static final String TEST_CLASS_2 = "com.google.android.gts.SampleDeviceTest";
  private static final String TEST_CASE_1 = "testScreenshot";
  private static final String TEST_CASE_2 = "testDeviceScreenshot";
  private static final String SCREENSHOT_FILE_1 = "screenshot.png";
  private static final String SCREENSHOT_FILE_2 =
      String.format("%s__%s__%s", TEST_CLASS_1, INTERACTIVE_STEP_1, SCREENSHOT_SUFFIX);
  private static final String SCREENSHOT_FILE_3 =
      String.format(
          "%s#%s__%s__%s", TEST_CLASS_1, TEST_CASE_1, INTERACTIVE_STEP_1, SCREENSHOT_SUFFIX);
  private static final String SCREENSHOT_FILE_4 =
      String.format(
          "%s#%s__%s__%s", TEST_CLASS_1, TEST_CASE_1, INTERACTIVE_STEP_2, SCREENSHOT_SUFFIX);
  private static final String SCREENSHOT_FILE_5 =
      String.format(
          "%s#%s__%s__%s", TEST_CLASS_2, TEST_CASE_1, INTERACTIVE_STEP_1, SCREENSHOT_SUFFIX);
  private static final String SCREENSHOT_FILE_6 =
      String.format(
          "%s#%s__%s__%s", TEST_CLASS_2, TEST_CASE_2, INTERACTIVE_STEP_2, SCREENSHOT_SUFFIX);

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Inject private LocalFileUtil localFileUtil;
  @Inject private ScreenshotsMetadataUtil screenshotsMetadataUtil;

  @Before
  public void setUp() throws Exception {
    Guice.createInjector().injectMembers(this);
  }

  @Test
  public void genScreenshotsMetadataFile_noScreenshotsDir_doesNothing() throws Exception {
    File resultDir =
        getFakeResultDir(temporaryFolder, "result-dir", /* withScreenshotDir= */ false);

    assertThat(screenshotsMetadataUtil.genScreenshotsMetadataFile(resultDir.toPath())).isEmpty();
  }

  @Test
  public void isScreenshotFile_byFileExtension() {
    assertThat(ScreenshotsMetadataUtil.isScreenshotFile(Path.of("tmp/screenshot_1.png"))).isTrue();
    assertThat(ScreenshotsMetadataUtil.isScreenshotFile(Path.of("screenshot_2.jpeg"))).isTrue();
    assertThat(ScreenshotsMetadataUtil.isScreenshotFile(Path.of("../screenshot_3.jpg"))).isTrue();
    assertThat(ScreenshotsMetadataUtil.isScreenshotFile(Path.of("screenshot_4"))).isFalse();
  }

  @Test
  public void genScreenshotsMetadataFile_verifyFileContent() throws Exception {
    File resultDir = getFakeResultDir(temporaryFolder, "result-dir", /* withScreenshotDir= */ true);
    File screenshotsDir = new File(resultDir, SCREENSHOTS_DIR_NAME);
    String moduleName1 = "testModule1";
    String moduleName2 = "testModule2";
    prepareModuleDir(
        screenshotsDir, moduleName1 + "__x86", Arrays.asList(SCREENSHOT_FILE_3, SCREENSHOT_FILE_6));
    prepareModuleDir(screenshotsDir, moduleName2, Arrays.asList(SCREENSHOT_FILE_1));
    new File(screenshotsDir, SCREENSHOT_FILE_1).createNewFile();

    Optional<Path> screenshotsMetadataFile =
        screenshotsMetadataUtil.genScreenshotsMetadataFile(resultDir.toPath());

    assertThat(screenshotsMetadataFile).isPresent();
    String xmlContent = localFileUtil.readFile(screenshotsMetadataFile.get());
    verifyModuleTags(xmlContent, Arrays.asList(moduleName1, moduleName2, DEFAULT_MODULE_NAME));
    verifyTestCaseTags(xmlContent, Arrays.asList(TEST_CLASS_1, TEST_CLASS_2, SCREENSHOT_FILE_1));
    verifyTestTags(xmlContent, Arrays.asList(TEST_CASE_1, TEST_CASE_2, SCREENSHOT_FILE_1));
    verifyScreenshotTags(
        xmlContent, Arrays.asList(SCREENSHOT_FILE_3, SCREENSHOT_FILE_6, SCREENSHOT_FILE_1));
  }

  @Test
  public void genScreenshotsMetadataFile_overwriteFileContent() throws Exception {
    File resultDir = getFakeResultDir(temporaryFolder, "result-dir", /* withScreenshotDir= */ true);
    File screenshotsDir = new File(resultDir, SCREENSHOTS_DIR_NAME);

    // Prepares screenshots metadata file with old content.
    localFileUtil.writeToFile(
        ScreenshotsMetadataUtil.getScreenshotsMetadataFilePath(resultDir.toPath())
            .toAbsolutePath()
            .toString(),
        "Old content");

    // Generates screenshots metadata file with new content.
    String moduleName1 = "testModule1";
    String moduleName2 = "testModule2";
    prepareModuleDir(
        screenshotsDir, moduleName1 + "__x86", Arrays.asList(SCREENSHOT_FILE_3, SCREENSHOT_FILE_6));
    prepareModuleDir(screenshotsDir, moduleName2, Arrays.asList(SCREENSHOT_FILE_1));
    new File(screenshotsDir, SCREENSHOT_FILE_1).createNewFile();

    Optional<Path> screenshotsMetadataFile =
        screenshotsMetadataUtil.genScreenshotsMetadataFile(resultDir.toPath());

    assertThat(screenshotsMetadataFile).isPresent();
    assertThat(localFileUtil.readFile(screenshotsMetadataFile.get())).doesNotContain("Old content");
  }

  private static void prepareModuleDir(
      File screenshotsDir, String moduleNameWithAbi, List<String> screenshotNames)
      throws Exception {
    File moduleDir = new File(screenshotsDir, moduleNameWithAbi);
    moduleDir.mkdirs();
    for (String screenshotName : screenshotNames) {
      new File(moduleDir, screenshotName).createNewFile();
    }
  }

  @Test
  public void addScreenshotTags_verifyXmlContent() throws Exception {
    XmlSerializer serializer = XmlPullParserFactory.newInstance().newSerializer();
    StringWriter writer = new StringWriter();
    serializer.setOutput(writer);
    serializer.startDocument(ENCODING, false);
    serializer.startTag(NS, MODULE_TAG);
    ScreenshotsMetadataUtil.addScreenshotTags(
        serializer, Arrays.asList(SCREENSHOT_FILE_1, SCREENSHOT_FILE_3, SCREENSHOT_FILE_6));
    serializer.endTag(NS, MODULE_TAG);
    serializer.endDocument();

    String xmlContent = writer.toString();

    verifyTestCaseTags(xmlContent, Arrays.asList(SCREENSHOT_FILE_1, TEST_CLASS_1, TEST_CLASS_2));
    verifyTestTags(xmlContent, Arrays.asList(SCREENSHOT_FILE_1, TEST_CASE_1, TEST_CASE_2));
    verifyScreenshotTags(
        xmlContent, Arrays.asList(SCREENSHOT_FILE_1, SCREENSHOT_FILE_3, SCREENSHOT_FILE_6));
  }

  @Test
  public void getScreenshotTagDatas_verifyResultSorted() {
    Map<String, Map<String, List<ScreenshotTagData>>> screenshotTagDatas =
        ScreenshotsMetadataUtil.getScreenshotTagDatas(
            Arrays.asList(
                SCREENSHOT_FILE_4, SCREENSHOT_FILE_3, SCREENSHOT_FILE_5, SCREENSHOT_FILE_6));

    verifyKeys(screenshotTagDatas.keySet(), Arrays.asList(TEST_CLASS_2, TEST_CLASS_1));

    Map<String, List<ScreenshotTagData>> tagDataOfClass = screenshotTagDatas.get(TEST_CLASS_1);
    verifyKeys(tagDataOfClass.keySet(), Arrays.asList(TEST_CASE_1));
    verifyScreenshotTagDatas(
        tagDataOfClass.get(TEST_CASE_1),
        Arrays.asList(
            ScreenshotTagData.create(
                TEST_CLASS_1, TEST_CASE_1, SCREENSHOT_FILE_4, INTERACTIVE_STEP_2),
            ScreenshotTagData.create(
                TEST_CLASS_1, TEST_CASE_1, SCREENSHOT_FILE_3, INTERACTIVE_STEP_1)));

    tagDataOfClass = screenshotTagDatas.get(TEST_CLASS_2);
    verifyKeys(tagDataOfClass.keySet(), Arrays.asList(TEST_CASE_2, TEST_CASE_1));
    verifyScreenshotTagDatas(
        tagDataOfClass.get(TEST_CASE_1),
        Arrays.asList(
            ScreenshotTagData.create(
                TEST_CLASS_2, TEST_CASE_1, SCREENSHOT_FILE_5, INTERACTIVE_STEP_1)));
    verifyScreenshotTagDatas(
        tagDataOfClass.get(TEST_CASE_2),
        Arrays.asList(
            ScreenshotTagData.create(
                TEST_CLASS_2, TEST_CASE_2, SCREENSHOT_FILE_6, INTERACTIVE_STEP_2)));
  }

  @Test
  public void getScreenshotTagData_withoutStepInfo() {
    verifyScreenshotTagData(
        ScreenshotsMetadataUtil.getScreenshotTagData(SCREENSHOT_FILE_1),
        ScreenshotTagData.create(
            SCREENSHOT_FILE_1, SCREENSHOT_FILE_1, SCREENSHOT_FILE_1, SCREENSHOT_FILE_1));
  }

  @Test
  public void getScreenshotTagData_withoutTestInfo() {
    verifyScreenshotTagData(
        ScreenshotsMetadataUtil.getScreenshotTagData(SCREENSHOT_FILE_2),
        ScreenshotTagData.create(
            TEST_CLASS_1, TEST_CLASS_1, SCREENSHOT_FILE_2, INTERACTIVE_STEP_1));
  }

  @Test
  public void getScreenshotTagData_withTestInfo() {
    verifyScreenshotTagData(
        ScreenshotsMetadataUtil.getScreenshotTagData(SCREENSHOT_FILE_3),
        ScreenshotTagData.create(TEST_CLASS_1, TEST_CASE_1, SCREENSHOT_FILE_3, INTERACTIVE_STEP_1));
  }

  private static void verifyModuleTags(String xmlContent, List<String> moduleNames) {
    for (String moduleName : moduleNames) {
      verifyXmlContent(xmlContent, MODULE_TAG, moduleName);
    }
  }

  private static void verifyTestCaseTags(String xmlContent, List<String> testCaseNames) {
    for (String testCaseName : testCaseNames) {
      verifyXmlContent(xmlContent, CASE_TAG, testCaseName);
    }
  }

  private static void verifyTestTags(String xmlContent, List<String> testNames) {
    for (String testName : testNames) {
      verifyXmlContent(xmlContent, TEST_TAG, testName);
    }
  }

  private static void verifyScreenshotTags(String xmlContent, List<String> screenshotNames) {
    for (String screenshotName : screenshotNames) {
      verifyXmlContent(xmlContent, SCREENSHOT_TAG, screenshotName);
    }
  }

  private static void verifyXmlContent(String xmlContent, String tagName, String nameAttr) {
    assertThat(xmlContent).contains(String.format("<%s name=\"%s\"", tagName, nameAttr));
  }

  private static void verifyKeys(Set<String> keys, List<String> expected) {
    int i = 0;
    for (String key : keys) {
      assertThat(key).isEqualTo(expected.get(i++));
    }
    assertThat(i).isEqualTo(expected.size());
  }

  private static void verifyScreenshotTagDatas(
      List<ScreenshotTagData> results, List<ScreenshotTagData> expected) {
    assertThat(results).hasSize(expected.size());
    for (int i = 0; i < results.size(); i++) {
      verifyScreenshotTagData(results.get(i), expected.get(i));
    }
  }

  private static void verifyScreenshotTagData(
      ScreenshotTagData result, ScreenshotTagData expected) {
    assertThat(result.testCaseName()).isEqualTo(expected.testCaseName());
    assertThat(result.testName()).isEqualTo(expected.testName());
    assertThat(result.screenshotName()).isEqualTo(expected.screenshotName());
    assertThat(result.screenshotDescription()).isEqualTo(expected.screenshotDescription());
  }

  private File getFakeResultDir(
      TemporaryFolder temporaryFolder, String resultDirName, boolean withScreenshotDir)
      throws Exception {
    File rootDir = temporaryFolder.newFolder("cts-root-dir");
    File resultDir = new File(rootDir, "android-cts/results/" + resultDirName);
    resultDir.mkdirs();
    if (withScreenshotDir) {
      new File(resultDir, SCREENSHOTS_DIR_NAME).mkdirs();
    }
    return resultDir;
  }
}
