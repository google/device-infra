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

package com.google.devtools.mobileharness.infra.controller.test.util.xtsdownloader;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.IMPORTANT;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.ByteStreams;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException.DesiredTestResult;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.ModuleInfo;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsConstants;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.protobuf.TextFormat;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** Built in lab plugin for MCTS test suites dynamic downloading. */
public class MctsDynamicDownloadPlugin implements XtsDynamicDownloadPlugin {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Object lock = new Object();

  private static final String STATIC_MCTS_TESTCASES_PATH = "/android/xts/mcts";

  private static final String TMP_MCTS_TESTCASES_PATH = "/android/xts/mcts/testcases";

  private static final String MCTS_JDK_PATH = "/android/xts/mcts/tool/jdk.zip";

  private static final String TMP_MCTS_TOOL_PATH = "/android/xts/mcts/tool";

  private static final String TMP_MCTS_JDK_PATH = "/android/xts/mcts/tool/jdk";

  private static final String MAINLINE_TVP_PKG = "com.google.android.modulemetadata";

  private static final String ANDROID_V_CODENAME = "VanillaIceCream";

  private static final String PRELOADED_KEY = "preloaded";

  private static final String NON_PRELOADED_KEY = "non-preloaded";

  private static final ImmutableMap<String, Integer> SDK_LEVEL_TO_YEAR =
      ImmutableMap.of(
          "30", 2020,
          "31", 2021,
          "32", 2022,
          "33", 2022,
          "34", 2023,
          "35", 2024);
  // For CTS, there's no diff between arm64 and arm.
  private static final ImmutableMap<String, String> DEVICE_ABI_MAP =
      ImmutableMap.of(
          "armeabi", "arm64",
          "armeabi-v7a", "arm64",
          "armeabi-v7a-hard", "arm64",
          "arm64-v8a", "arm64",
          "x86", "x86_64",
          "x86_64", "x86_64");
  private final AndroidPackageManagerUtil androidPackageManagerUtil;
  private final AndroidAdbUtil adbUtil;
  private final LocalFileUtil fileUtil;
  private final ResUtil resUtil = new ResUtil();

  public MctsDynamicDownloadPlugin() {
    this.adbUtil = new AndroidAdbUtil();
    this.fileUtil = new LocalFileUtil();
    this.androidPackageManagerUtil = new AndroidPackageManagerUtil();
  }

  @VisibleForTesting
  MctsDynamicDownloadPlugin(
      AndroidAdbUtil adbUtil, AndroidPackageManagerUtil androidPackageManagerUtil) {
    this.adbUtil = adbUtil;
    this.androidPackageManagerUtil = androidPackageManagerUtil;
    this.fileUtil = new LocalFileUtil();
  }

  @Override
  public XtsDynamicDownloadInfo parse(TestInfo test, String deviceId)
      throws MobileHarnessException, InterruptedException {
    ImmutableList<String> preloadedMainlineModules = getPreloadedMainlineModules(deviceId);
    ListMultimap<String, String> mctsNamesOfPreloadedMainlineModules =
        getMctsNamesOfPreloadedMainlineModules(preloadedMainlineModules);
    String deviceAbi = DEVICE_ABI_MAP.get(adbUtil.getProperty(deviceId, AndroidProperty.ABI));
    if (deviceAbi == null) {
      throw new MobileHarnessException(
          AndroidErrorId.XTS_DYNAMIC_DOWNLOADER_DEVICE_ABI_NOT_SUPPORT,
          String.format(
              "The ABI of device %s is not compatible with the xts dynamic downloader.", deviceId));
    }
    // Before V release, the SDK level is 34, it will be increased to 35 only after V final release.
    String aospVersion =
        adbUtil.getProperty(deviceId, AndroidProperty.CODENAME).equals(ANDROID_V_CODENAME)
            ? "35"
            : adbUtil.getProperty(deviceId, AndroidProperty.SDK_VERSION);
    List<String> downloadLinkUrls = new ArrayList<>();
    // Add the Lorry download link url of MCTS file for preloaded mainline modules. For example:
    // https://dl.google.com/dl/android/xts/mcts/YYYY-MM/arm64/android-mcts-<module_name>.zip
    if (mctsNamesOfPreloadedMainlineModules.containsKey(PRELOADED_KEY)) {
      String versioncode =
          preloadedMainlineModules.contains(MAINLINE_TVP_PKG)
              ? Integer.toString(
                  androidPackageManagerUtil.getAppVersionCode(deviceId, MAINLINE_TVP_PKG))
              : "310000000";
      // if the TVP version is 310000000, that means all the mainline modules were built
      // from source, rather than prebuilt dropped. 310000000 is just the default value in
      // http://ac/vendor/unbundled_google/modules/ModuleMetadataGoogle/Primary_AndroidManifest.xml
      String preloadedMainlineVersion =
          versioncode.equals("310000000")
              ? aospVersion
              : getPreloadedMainlineVersion(versioncode, aospVersion);
      for (String mctsName : mctsNamesOfPreloadedMainlineModules.get(PRELOADED_KEY)) {
        String downloadUrl =
            String.format(
                "https://dl.google.com/dl/android/xts/mcts/%s/%s/%s.zip",
                preloadedMainlineVersion, deviceAbi, mctsName);
        downloadLinkUrls.add(downloadUrl);
      }
    }
    // Add the Lorry download link url of MCTS file for non-preloaded mainline modules. For example:
    // https://dl.google.com/dl/android/xts/mcts/{SDK_VERSION}/arm64/android-mcts-<module_name>.zip
    for (String mctsName : mctsNamesOfPreloadedMainlineModules.get(NON_PRELOADED_KEY)) {
      String downloadUrl =
          String.format(
              "https://dl.google.com/dl/android/xts/mcts/%s/%s/%s.zip",
              aospVersion, deviceAbi, mctsName);
      downloadLinkUrls.add(downloadUrl);
    }
    return XtsDynamicDownloadInfo.newBuilder()
        .setXtsType("cts")
        .setProject(XtsDynamicDownloadInfo.Project.MAINLINE)
        .addAllDownloadUrl(downloadLinkUrls)
        .build();
  }

  @Override
  public void downloadXtsFiles(XtsDynamicDownloadInfo xtsDynamicDownloadInfo, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    Set<String> allTestModules = new HashSet<>();
    for (String downloadUrl : xtsDynamicDownloadInfo.getDownloadUrlList()) {
      logger.atInfo().log("Start to download: %s", downloadUrl);
      String subDirName = downloadUrl.replace("https://dl.google.com/dl", "");
      String filePath = downloadPublicUrlFiles(downloadUrl, subDirName);
      allTestModules.addAll(unzipDownloadedTestCases(testInfo, filePath, subDirName));
    }
    testInfo
        .properties()
        .add(XtsConstants.XTS_DYNAMIC_DOWNLOAD_PATH_TEST_PROPERTY_KEY, TMP_MCTS_TESTCASES_PATH);
    // Print out all the downloaded MCTS test modules
    logger.atInfo().log("Downloaded MCTS test modules:");
    for (String testModule : allTestModules) {
      logger.atInfo().log("%s", testModule);
    }
    // Download the JDK file.
    String jdkFilePath =
        downloadPublicUrlFiles("https://dl.google.com/dl" + MCTS_JDK_PATH, MCTS_JDK_PATH);
    if (jdkFilePath != null) {
      fileUtil.unzipFile(jdkFilePath, testInfo.getTmpFileDir() + TMP_MCTS_TOOL_PATH);
      testInfo
          .properties()
          .add(XtsConstants.XTS_DYNAMIC_DOWNLOAD_PATH_JDK_PROPERTY_KEY, TMP_MCTS_JDK_PATH);
      logger.atInfo().log("Downloaded MCTS JDK files");
    }
  }

  @Subscribe
  void onTestStarting(LocalTestStartingEvent event) throws InterruptedException, SkipTestException {
    try {
      logger
          .atInfo()
          .with(IMPORTANCE, IMPORTANT)
          .log(
              "Start to download MCTS (this will only happen at the first run, might take 10+"
                  + " minutes) and prepare the test modules (this will take <1 minute), please"
                  + " wait... (You can also go to"
                  + " https://android.googlesource.com/platform/cts/+/main/tools/mcts/download_mcts.sh"
                  + " to use the script to manually download the files in advance to skip the"
                  + " downloading step)");
      XtsDynamicDownloadInfo xtsDynamicDownloadInfo =
          parse(event.getTest(), event.getDeviceLocator().getSerial());
      downloadXtsFiles(xtsDynamicDownloadInfo, event.getTest());
      logger.atInfo().with(IMPORTANCE, IMPORTANT).log("Finished MCTS test modules preparation.");
    } catch (MobileHarnessException e) {
      throw SkipTestException.create(
          "Failed to download Mainline CTS (MCTS). Either the files are broken, or the disk is"
              + " full, please reboot your PC to remove the outdated tmp files and check your"
              + " network connection then restart the CTS to retry.",
          DesiredTestResult.ERROR,
          AndroidErrorId.XTS_DYNAMIC_DOWNLOADER_FILE_NOT_FOUND,
          e);
    }
  }

  private ImmutableList<String> getPreloadedMainlineModules(String deviceId)
      throws InterruptedException {
    try {
      return androidPackageManagerUtil.listModuleInfos(deviceId).stream()
          .map(ModuleInfo::packageName)
          .collect(toImmutableList());
    } catch (MobileHarnessException e) {
      logger.atInfo().log(
          "Cannot get preloaded module info, handle this exception since this device might be built"
              + " from AOSP.");
      return ImmutableList.of();
    }
  }

  private ListMultimap<String, String> getMctsNamesOfPreloadedMainlineModules(
      ImmutableList<String> moduleList) throws MobileHarnessException {
    String configFilePath =
        resUtil.getResourceFile(
            getClass(),
            "/devtools/mobileharness/infra/controller/test/util/xtsdownloader/configs/module_info_map.textpb");
    String configTextProto = fileUtil.readFile(configFilePath);
    ModuleInfoMap.Builder moduleInfoMapBuilder = ModuleInfoMap.newBuilder();
    try {
      TextFormat.merge(configTextProto, moduleInfoMapBuilder);
    } catch (IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.XTS_DYNAMIC_DOWNLOADER_CONFIG_READER_ERROR,
          "Failed to read the Mainline module info map for xts dynamic downloader.",
          e);
    }
    ModuleInfoMap moduleInfoMap = moduleInfoMapBuilder.build();
    // To save two lists, one contains all the mcts names of preloaded modules, the other contain
    // the ones of non-preloaded modules.
    ListMultimap<String, String> mctsNamesOfAllModules = ArrayListMultimap.create();
    Set<String> preloadedModulesMcts = new HashSet<>(); // Track modules added to 'preloaded'
    Map<String, String> modulePackageToModuleInfoMap =
        moduleInfoMap.getModulePackageToModuleInfoMap();
    moduleList.forEach(
        moduleName -> {
          if (modulePackageToModuleInfoMap.containsKey(moduleName)) {
            preloadedModulesMcts.add(modulePackageToModuleInfoMap.get(moduleName));
          }
          ;
        });
    // Put the preloaded modules on the preloaded list.
    mctsNamesOfAllModules.putAll(PRELOADED_KEY, preloadedModulesMcts);
    // Put the non-preloaded modules on the non-preloaded list.
    Set<String> nonPreloadMctsList = new HashSet<>();
    nonPreloadMctsList.addAll(modulePackageToModuleInfoMap.values());
    nonPreloadMctsList.removeAll(preloadedModulesMcts);
    mctsNamesOfAllModules.putAll(NON_PRELOADED_KEY, nonPreloadMctsList);
    return mctsNamesOfAllModules;
  }

  private String getPreloadedMainlineVersion(String versioncode, String aospVersion)
      throws MobileHarnessException {
    // Get the release time of the preloaded mainline train, the format is YYYY-MM.
    // Note that version codes must always increase to successfully install newer builds. For this
    // reason, the version code "wraps" in January, making the month digits wrap to 13, instead of
    // 01 (for the first month of the year) and so on, if the month is 0 then it's aosp version.
    int month = Integer.parseInt(versioncode, 2, 4, 10);
    if (month == 0) {
      return aospVersion;
    }
    Integer sdkLevelYear = SDK_LEVEL_TO_YEAR.get(versioncode.substring(0, 2));
    if (sdkLevelYear == null) {
      throw new MobileHarnessException(
          AndroidErrorId.XTS_DYNAMIC_DOWNLOADER_DEVICE_SDK_VERSION_NOT_SUPPORT,
          "Device is not compatible with the xts dynamic downloader. Required R+ build.");
    }
    int year = sdkLevelYear + month / 12;
    String version = String.format("%d-%02d", year, (month % 12 == 0 ? 12 : month % 12));
    logger.atInfo().log("Get mainline train version(YYYY-MM): %s", version);
    return version;
  }

  @Nullable
  String downloadPublicUrlFiles(String downloadUrl, String subDirName)
      throws MobileHarnessException, InterruptedException {
    synchronized (lock) {
      // tmp/dynamic_download/android/xts/mcts/YYYY-MM/arm64/android-mcts-<module_name>.zip
      String dynamicDownloadDir =
          Flags.instance().xtsResDirRoot.getNonNull() + "/mcts_dynamic_download";
      String filePath = PathUtil.join(dynamicDownloadDir, subDirName);
      URLConnection connection = null;
      try {
        // get the last modified time of the url, will be 0 if the url not exist.
        URI uri = new URI(downloadUrl);
        URL url = uri.toURL();
        connection = url.openConnection();
        long urlLastModified = connection.getLastModified();
        if (urlLastModified == 0) {
          logger.atInfo().log("Url %s not exist.", downloadUrl);
          return null;
        }
        // check the file exists and the last modified time.
        if (fileUtil.isFileExist(filePath)) {
          long fileLastModified = fileUtil.getFileLastModifiedTime(filePath).toEpochMilli();
          // check if the zip file is valid and up to date.
          if (urlLastModified < fileLastModified && fileUtil.isZipFileValid(filePath)) {
            logger.atInfo().log("File %s is up to date, skip downloading.", filePath);
            return filePath;
          } else {
            logger.atInfo().log(
                "File %s is out of date or broken, need to download again.", filePath);
            fileUtil.removeFileOrDir(filePath);
          }
        } else {
          logger.atInfo().log("File %s does not exist, needs to download the file.", filePath);
        }
      } catch (IOException | URISyntaxException e) {
        throw new MobileHarnessException(
            AndroidErrorId.XTS_DYNAMIC_DOWNLOADER_FILE_DOWNLOAD_ERROR,
            String.format("An I/O error occurred opening the URLConnection to %s", downloadUrl),
            e);
      }
      // disable caching.
      connection.setDefaultUseCaches(false);
      // Preparer the target directory:
      fileUtil.prepareDir(fileUtil.getParentDirPath(filePath), LocalFileUtil.FULL_ACCESS);
      // Download the resource.
      try (InputStream inputStream = new BufferedInputStream(connection.getInputStream());
          FileOutputStream outputStream = new FileOutputStream(filePath)) {
        ByteStreams.copy(inputStream, outputStream);
        fileUtil.grantFileOrDirFullAccess(dynamicDownloadDir);
        fileUtil.grantFileOrDirFullAccess(filePath);
        logger.atInfo().log("Downloaded resource %s to %s", downloadUrl, filePath);
        return filePath;
      } catch (IOException e) {
        if (e instanceof FileNotFoundException) {
          // Handle FileNotFoundException specifically since there might not exist MCTS files for
          // some of the modules.
          logger.atWarning().log(
              "%s not exist, since there might not exist MCTS files for some of the modules.",
              downloadUrl);
          return null;
        } else {
          throw new MobileHarnessException(
              AndroidErrorId.XTS_DYNAMIC_DOWNLOADER_FILE_DOWNLOAD_ERROR,
              String.format(
                  "An I/O error occurred when downloading and unzipping resource from %s to %s",
                  downloadUrl, filePath),
              e);
        }
      }
    }
  }

  private Set<String> unzipDownloadedTestCases(
      TestInfo testInfo, String filePath, String subDirName)
      throws MobileHarnessException, InterruptedException {
    if (filePath == null) {
      return new HashSet<>();
    }
    // unzip the file to /tmp/android/xts/mcts/android-mcts-<module>/testcases
    String unzipDirPath = testInfo.getTmpFileDir() + STATIC_MCTS_TESTCASES_PATH;
    fileUtil.unzipFile(filePath, unzipDirPath);
    // mv all the mcts test cases to /tmp/android/xts/mcts/testcases/
    List<String> listPaths =
        fileUtil.listFileOrDirPaths(
            unzipDirPath + "/" + PathUtil.basename(subDirName).replace(".zip", "") + "/testcases");
    Set<String> testModules = new HashSet<>(); // Track MCTS test modules.
    for (String path : listPaths) {
      String desPath = unzipDirPath + "/testcases";
      // Skip moving the files that already existed. For example, CtsDeviceInfo contained in all the
      // android-mcts-<module>.zip.
      if (!fileUtil.getFileOrDir(desPath + "/" + PathUtil.basename(path)).exists()) {
        fileUtil.moveFileOrDir(path, desPath);
        logger.atInfo().log("Moved test cases from link [%s] to [%s]", path, unzipDirPath);
        testModules.add(PathUtil.basename(path));
      }
    }
    logger.atInfo().log("Unzipped resource to %s", unzipDirPath);
    return testModules;
  }
}
