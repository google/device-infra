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

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import javax.inject.Inject;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

/** A utility class to generate the screenshots metadata file for xTS Interactive tests. */
public class ScreenshotsMetadataUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // The default directory under the result directory which contains all screenshot files of xTS
  // Interactive tests.
  @VisibleForTesting static final String SCREENSHOTS_DIR_NAME = "screenshots";

  // The name of the XML file that contains the info of all screenshot files taken during xTS
  // Interactive tests' execution.
  @VisibleForTesting
  static final String SCREENSHOTS_METADATA_FILE_NAME = "screenshots_metadata.xml";

  // XML constants
  @VisibleForTesting static final String ENCODING = "UTF-8";
  @VisibleForTesting static final String NS = null;
  @VisibleForTesting static final String NAME_ATTR = "name";
  @VisibleForTesting static final String ABI_ATTR = "abi";
  private static final String DESCRIPTION_ATTR = "description";

  private static final String RESULT_TAG = "Result";
  @VisibleForTesting static final String MODULE_TAG = "Module";
  @VisibleForTesting static final String CASE_TAG = "TestCase";
  @VisibleForTesting static final String TEST_TAG = "Test";
  private static final String SCREENSHOTS_TAG = "Screenshots";
  @VisibleForTesting static final String SCREENSHOT_TAG = "Screenshot";

  // Default module name for all screenshot files that don't belong to a module.
  @VisibleForTesting static final String DEFAULT_MODULE_NAME = "UNKNOWN_MODULE";

  /** A model that contains the required data to create a new screenshot tag in an XML tree. */
  @AutoValue
  abstract static class ScreenshotTagData {

    abstract String testCaseName();

    abstract String testName();

    abstract String screenshotName();

    abstract String screenshotDescription();

    static ScreenshotTagData create(
        String testCaseName, String testName, String screenshotName, String screenshotDescription) {
      return new AutoValue_ScreenshotsMetadataUtil_ScreenshotTagData(
          testCaseName, testName, screenshotName, screenshotDescription);
    }
  }

  private final LocalFileUtil localFileUtil;

  @Inject
  ScreenshotsMetadataUtil(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
  }

  /** Generates the screenshots metadata file under the given result directory. */
  public Optional<Path> genScreenshotsMetadataFile(Path resultDir) throws MobileHarnessException {
    try {
      return genScreenshotsMetadataFileHelper(resultDir);
    } catch (IOException e) {
      throw new MobileHarnessException(
          ExtErrorId.SCREENSHOTS_METADATA_UTIL_GEN_METADATA_FILE_ERROR,
          String.format("Failed to generate screenshots metadata file under %s", resultDir),
          e);
    }
  }

  private Optional<Path> genScreenshotsMetadataFileHelper(Path resultDir)
      throws MobileHarnessException, IOException {
    Path screenshotsDir = resultDir.resolve(SCREENSHOTS_DIR_NAME);
    if (!localFileUtil.isDirExist(screenshotsDir)) {
      logger.atInfo().log("No screenshot files found in %s", resultDir);
      return Optional.empty();
    }

    Path screenshotsMetadataFile = getScreenshotsMetadataFilePath(resultDir);
    OutputStream stream = new FileOutputStream(screenshotsMetadataFile.toFile());
    XmlSerializer serializer = newXmlSerializer(stream);
    serializer.setOutput(stream, ENCODING);
    serializer.startDocument(ENCODING, false);
    serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
    serializer.processingInstruction(
        "xml-stylesheet type=\"text/xsl\" href=\"compatibility_result.xsl\"");
    serializer.startTag(NS, RESULT_TAG);

    List<String> moduleNameWithAbis = new ArrayList<>();
    List<String> screenshotsInRoot = new ArrayList<>();
    localFileUtil
        .listFilesOrDirs(screenshotsDir, path -> true)
        .forEach(
            fileOrDir -> {
              if (localFileUtil.isDirExist(fileOrDir)) {
                moduleNameWithAbis.add(fileOrDir.getFileName().toString());
              } else if (isScreenshotFile(fileOrDir)) {
                screenshotsInRoot.add(fileOrDir.getFileName().toString());
              }
            });

    // To keep module names in the metadata XML sorted.
    Collections.sort(moduleNameWithAbis);
    for (String moduleNameWithAbi : moduleNameWithAbis) {
      serializer.startTag(NS, MODULE_TAG);
      addModuleTagAttributes(serializer, moduleNameWithAbi);

      List<String> screenshotsOfModule = new ArrayList<>();
      localFileUtil
          .listFilePaths(screenshotsDir.resolve(moduleNameWithAbi), /* recursively= */ false)
          .stream()
          .forEach(
              file -> {
                if (isScreenshotFile(file)) {
                  screenshotsOfModule.add(file.getFileName().toString());
                }
              });
      addScreenshotTags(serializer, screenshotsOfModule);

      serializer.endTag(NS, MODULE_TAG);
    }

    // All screenshots under the root directory are under the default module.
    if (!screenshotsInRoot.isEmpty()) {
      serializer.startTag(NS, MODULE_TAG);
      serializer.attribute(NS, NAME_ATTR, DEFAULT_MODULE_NAME);

      // No need to sort screenshotsInRoot as the tags map is sorted.
      addScreenshotTags(serializer, screenshotsInRoot);

      serializer.endTag(NS, MODULE_TAG);
    }

    serializer.endTag(NS, RESULT_TAG);
    serializer.endDocument();
    logger.atInfo().log(
        "Successfully generated the screenshots metadata file: %s", screenshotsMetadataFile);
    return Optional.of(screenshotsMetadataFile);
  }

  @VisibleForTesting
  static Path getScreenshotsMetadataFilePath(Path resultDir) {
    return resultDir.resolve(SCREENSHOTS_DIR_NAME).resolve(SCREENSHOTS_METADATA_FILE_NAME);
  }

  /** Adds the name and abi attributes (if have) for the <Module> tag. */
  private static void addModuleTagAttributes(XmlSerializer serializer, String moduleNameWithAbi)
      throws IOException {
    List<String> splitModuleAbis = Splitter.on("__").splitToList(moduleNameWithAbi);
    if (splitModuleAbis.size() == 2) {
      serializer.attribute(NS, NAME_ATTR, splitModuleAbis.get(0));
      serializer.attribute(NS, ABI_ATTR, splitModuleAbis.get(1));
    } else {
      serializer.attribute(NS, NAME_ATTR, moduleNameWithAbi);
    }
  }

  /** Checks if the given {@link Path} is a screenshot file. */
  @VisibleForTesting
  static boolean isScreenshotFile(Path filePath) {
    String extSuffix = Ascii.toLowerCase(filePath.getFileName().toString());
    return extSuffix.endsWith(".png") || extSuffix.endsWith(".jpeg") || extSuffix.endsWith(".jpg");
  }

  /** Parses a list of screenshot file names to add tags into the given {@code XmlSerializer}. */
  @VisibleForTesting
  static void addScreenshotTags(XmlSerializer serializer, List<String> screenshotFileNames)
      throws IOException {
    Map<String, Map<String, List<ScreenshotTagData>>> screenshotTagDatas =
        getScreenshotTagDatas(screenshotFileNames);
    for (String testCaseName : screenshotTagDatas.keySet()) {
      serializer.startTag(NS, CASE_TAG);
      serializer.attribute(NS, NAME_ATTR, testCaseName);

      Map<String, List<ScreenshotTagData>> testCaseScreenshotTagDatas =
          screenshotTagDatas.get(testCaseName);
      for (String testName : testCaseScreenshotTagDatas.keySet()) {
        serializer.startTag(NS, TEST_TAG);
        serializer.attribute(NS, NAME_ATTR, testName);
        serializer.startTag(NS, SCREENSHOTS_TAG);

        List<ScreenshotTagData> testScreenshotTagDatas = testCaseScreenshotTagDatas.get(testName);
        for (ScreenshotTagData tagData : testScreenshotTagDatas) {
          serializer.startTag(NS, SCREENSHOT_TAG);
          serializer.attribute(NS, NAME_ATTR, tagData.screenshotName());
          serializer.attribute(NS, DESCRIPTION_ATTR, tagData.screenshotDescription());
          serializer.endTag(NS, SCREENSHOT_TAG);
        }
        serializer.endTag(NS, SCREENSHOTS_TAG);
        serializer.endTag(NS, TEST_TAG);
      }
      serializer.endTag(NS, CASE_TAG);
    }
  }

  /**
   * Gets TestClass -> (TestCase -> List of screenshots mappings) mappings by the given list of
   * screenshot file names.
   */
  @VisibleForTesting
  static Map<String, Map<String, List<ScreenshotTagData>>> getScreenshotTagDatas(
      List<String> screenshotFileNames) {
    Map<String, Map<String, List<ScreenshotTagData>>> screenshotTagDatas = new TreeMap<>();
    for (String screenshotFileName : screenshotFileNames) {
      ScreenshotTagData screenshotTagData = getScreenshotTagData(screenshotFileName);
      Map<String, List<ScreenshotTagData>> testCaseScreenshotTagDatas =
          screenshotTagDatas.computeIfAbsent(
              screenshotTagData.testCaseName(), (String k) -> new TreeMap<>());

      testCaseScreenshotTagDatas.putIfAbsent(screenshotTagData.testName(), new ArrayList<>());
      testCaseScreenshotTagDatas.get(screenshotTagData.testName()).add(screenshotTagData);
    }
    return screenshotTagDatas;
  }

  /** Parses the given screenshot file name to get a {@link ScreenshotTagData}. */
  @VisibleForTesting
  static ScreenshotTagData getScreenshotTagData(String screenshotFileName) {
    List<String> screenshotDetails = Splitter.on("__").splitToList(screenshotFileName);
    // The length of the array is 3 if the screenshot is taken via Interactive framework.
    if (screenshotDetails.size() == 3) {
      List<String> testDetails = Splitter.on('#').splitToList(screenshotDetails.get(0));
      // If com.android.interactive.testrules.TestNameSaver is enabled,
      // the test class and test case are parsed. Otherwise aren't.
      if (testDetails.size() == 2) {
        return ScreenshotTagData.create(
            testDetails.get(0), testDetails.get(1), screenshotFileName, screenshotDetails.get(1));
      } else {
        logger.atWarning().log(
            "Found a screenshot that doesn't contain test package and class info: %s",
            screenshotFileName);
        return ScreenshotTagData.create(
            screenshotDetails.get(0),
            screenshotDetails.get(0),
            screenshotFileName,
            screenshotDetails.get(1));
      }
    } else {
      logger.atInfo().log(
          "Found a screenshot that isn't taken via Interactive library: %s", screenshotFileName);
      return ScreenshotTagData.create(
          screenshotFileName, screenshotFileName, screenshotFileName, screenshotFileName);
    }
  }

  private XmlSerializer newXmlSerializer(OutputStream outputStream) throws IOException {
    try {
      return XmlPullParserFactory.newInstance().newSerializer();
    } catch (XmlPullParserException e) {
      try {
        outputStream.close();
      } catch (IOException e2) {
        // ignored
      }
      throw new IOException(e);
    }
  }
}
