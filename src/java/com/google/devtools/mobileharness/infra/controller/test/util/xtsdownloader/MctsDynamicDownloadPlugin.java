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
import static org.apache.commons.lang3.SerializationUtils.deserialize;
import static org.apache.commons.lang3.SerializationUtils.serialize;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidSystemSpecUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsConstants;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.protobuf.TextFormat;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
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

  private static final String PRELOADED_KEY = "preloaded";

  private static final String NON_PRELOADED_KEY = "non-preloaded";

  private static final ImmutableSet<String> ANDROID_14_TV_THREAD_MODULES =
      ImmutableSet.of(
          "android-mcts-networkstack", "android-mcts-tethering", "android-mcts-dnsresolver");

  // Only consider the Module released in Android V+ and not beta version. For the month of platform
  // initial release ONLY, 5th digit >= 4 indicates platform beta (or mainline beta if >=8 or daily
  // if => 9), refer to b/413266608 for more details.
  private static final Pattern VERSIONCODE_PATTERN =
      Pattern.compile("(3[5-9]|[4-9][0-9])(?!(00|99))\\d{2}[0-7]\\d{4}");
  private static final Pattern PLATFORM_BETA_VERSIONCODE_PATTERN =
      Pattern.compile("(3610|3704|3710)[4-9]\\d{6}");

  // Add the versioncode from
  // android/platform/superproject/main/+/main:build/release/flag_declarations/RELEASE_DEFAULT_UPDATABLE_MODULE_VERSION.textproto
  private static final ImmutableSet<String> AOSP_VERSIONCODE_LIST = ImmutableSet.of("352090000");

  // Add the initial release versioncode for Android SDK release which we don't need to download
  // the MCTS files.
  private static final ImmutableMap<String, ImmutableList<String>> INITIAL_RELEASE_VERSIONCODE_MAP =
      ImmutableMap.of(
          "36", ImmutableList.of("2025-01", "2025-02", "2025-03", "2025-04", "2025-05", "2025-11"),
          "37", ImmutableList.of("2026-01", "2026-02", "2026-03", "2026-04"));

  private static final String MAINLINE_AOSP_VERSION_KEY = "AOSP";

  private static final ImmutableMap<String, Integer> SDK_LEVEL_TO_YEAR =
      ImmutableMap.of(
          "30", 2020,
          "31", 2021,
          "32", 2022,
          "33", 2022,
          "34", 2023,
          "35", 2024,
          "36", 2025,
          "37", 2026);
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
  private final AndroidAdbInternalUtil adbInternalUtil;
  private final AndroidSystemSpecUtil androidSystemSpecUtil;

  public MctsDynamicDownloadPlugin() {
    this.adbUtil = new AndroidAdbUtil();
    this.fileUtil = new LocalFileUtil();
    this.androidPackageManagerUtil = new AndroidPackageManagerUtil();
    this.adbInternalUtil = new AndroidAdbInternalUtil();
    this.androidSystemSpecUtil = new AndroidSystemSpecUtil();
  }

  @VisibleForTesting
  MctsDynamicDownloadPlugin(
      AndroidAdbUtil adbUtil,
      AndroidPackageManagerUtil androidPackageManagerUtil,
      AndroidAdbInternalUtil adbInternalUtil) {
    this.adbUtil = adbUtil;
    this.androidPackageManagerUtil = androidPackageManagerUtil;
    this.fileUtil = new LocalFileUtil();
    this.adbInternalUtil = adbInternalUtil;
    this.androidSystemSpecUtil = new AndroidSystemSpecUtil();
  }

  @Override
  @SuppressWarnings("BeforeSnippet")
  public XtsDynamicDownloadInfo parse(LocalTestStartingEvent event) throws MobileHarnessException {
    TestInfo test = event.getTest();
    String aospVersion = getAospVersion(event);
    ListMultimap<String, String> mctsNamesOfAllModules =
        getMctsNamesOfAllMainlineModules(event, aospVersion);

    String deviceAbiRaw = getAbiVersion(event);
    String deviceAbi = DEVICE_ABI_MAP.get(deviceAbiRaw);
    if (deviceAbi == null) {
      throw new MobileHarnessException(
          AndroidErrorId.XTS_DYNAMIC_DOWNLOADER_DEVICE_ABI_NOT_SUPPORT,
          "The ABI of device is not compatible with the xts dynamic downloader.");
    }

    List<String> downloadLinkUrls = new ArrayList<>();
    // Add the Lorry download link url of MCTS file for preloaded mainline modules. For example:
    // https://dl.google.com/dl/android/xts/mcts/YYYY-MM/arm64/android-mcts-<module_name>.zip
    if (mctsNamesOfAllModules.containsKey(PRELOADED_KEY)) {
      String versioncode = getTvpVersion(event);
      String preloadedMainlineVersion =
          processModuleVersion(versioncode, MAINLINE_TVP_PKG, aospVersion, aospVersion);
      test.properties()
          .add(XtsConstants.PRELOAD_MAINLINE_VERSION_TEST_PROPERTY_KEY, preloadedMainlineVersion);
      // Add the MCTS exclude file link url to the front of the list.
      downloadLinkUrls.add(
          String.format(
              "https://dl.google.com/dl/android/xts/mcts/tool/mcts_exclude/%s/%s/mcts-exclude.txt",
              aospVersion, preloadedMainlineVersion));
      // Add the full MCTS list file link url to the second position of the list.
      downloadLinkUrls.add(
          String.format(
              "https://dl.google.com/dl/android/xts/mcts/%s/%s/mcts_test_list.txt",
              preloadedMainlineVersion, deviceAbi));
      for (String mctsNameAndVersioncode : mctsNamesOfAllModules.get(PRELOADED_KEY)) {
        String moduleVersioncode =
            mctsNameAndVersioncode.substring(mctsNameAndVersioncode.indexOf(":") + 1);
        String downloadUrl =
            String.format(
                "https://dl.google.com/dl/android/xts/mcts/%s/%s/%s.zip",
                moduleVersioncode.equals(MAINLINE_AOSP_VERSION_KEY)
                    ? aospVersion
                    : moduleVersioncode,
                deviceAbi,
                mctsNameAndVersioncode.substring(0, mctsNameAndVersioncode.indexOf(":")));
        downloadLinkUrls.add(downloadUrl);
      }
    }
    // Add the Lorry download link url of MCTS file for non-preloaded mainline modules. For example:
    // https://dl.google.com/dl/android/xts/mcts/{SDK_VERSION}/arm64/android-mcts-<module_name>.zip
    for (String mctsName : mctsNamesOfAllModules.get(NON_PRELOADED_KEY)) {
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
  public void downloadXtsFiles(
      XtsDynamicDownloadInfo xtsDynamicDownloadInfo, LocalTestStartingEvent event)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = event.getTest();
    List<String> downloadUrlList = new ArrayList<>(xtsDynamicDownloadInfo.getDownloadUrlList());
    // Download the MCTS full test list.
    if (downloadUrlList.get(1).contains("mcts_test_list")) {
      logger.atInfo().log("Start to download MCTS full test list.");
      String mctsFullTestListUrl = downloadUrlList.get(1);
      String mctsFullTestListFilePath =
          downloadPublicUrlFiles(
              mctsFullTestListUrl, mctsFullTestListUrl.replace("https://dl.google.com/dl", ""));
      if (mctsFullTestListFilePath != null) {
        testInfo
            .properties()
            .add(
                XtsConstants.XTS_DYNAMIC_DOWNLOAD_PATH_TEST_LIST_PROPERTY_KEY,
                mctsFullTestListFilePath);
      }
      downloadUrlList.remove(1);
    }

    // If the job type is static, skip downloading the MCTS files.
    if (isStaticXtsJob(testInfo)) {
      return;
    }

    // Download the MCTS files for dynamic download mcts job.
    logger.atInfo().log("Start to download files for dynamic download MCTS job...");
    Set<String> allTestModules = new HashSet<>();
    Set<String> excludeTestModules = new HashSet<>();
    // Get the exclude test modules.
    if (downloadUrlList.get(0).contains("mcts_exclude")) {
      logger.atInfo().log("Start to download MCTS exclude module list.");
      String excludeTestModulesUrl = downloadUrlList.get(0);
      String excludeTestModulesFilePath =
          downloadPublicUrlFiles(
              excludeTestModulesUrl, excludeTestModulesUrl.replace("https://dl.google.com/dl", ""));
      if (excludeTestModulesFilePath != null) {
        excludeTestModules.addAll(fileUtil.readLineListFromFile(excludeTestModulesFilePath));
        // Print out all the exclude MCTS test modules
        logger.atInfo().log("MCTS exclude test modules:");
        for (String testModule : excludeTestModules) {
          logger.atInfo().log("%s", testModule);
        }
      }
      downloadUrlList.remove(0);
    }

    // Download the non-exclude MCTS test cases.
    for (String downloadUrl : downloadUrlList) {
      logger.atInfo().log("Start to download: %s", downloadUrl);
      String subDirName = downloadUrl.replace("https://dl.google.com/dl", "");
      String filePath = downloadPublicUrlFiles(downloadUrl, subDirName);
      allTestModules.addAll(
          unzipDownloadedTestCases(testInfo, filePath, subDirName, excludeTestModules));
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
    // Use train version to match JDK version.
    String jdkVersion = getTvpVersion(event).substring(0, 2);
    String jdkFileTargetPath = TMP_MCTS_TOOL_PATH + "/" + jdkVersion + "/jdk.zip";
    logger.atInfo().log("Start to download JDK files: %s", jdkFileTargetPath);
    String jdkFilePath =
        downloadPublicUrlFiles("https://dl.google.com/dl" + jdkFileTargetPath, MCTS_JDK_PATH);
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

      XtsDynamicDownloadInfo xtsDynamicDownloadInfo = parse(event);
      downloadXtsFiles(xtsDynamicDownloadInfo, event);

      logger.atInfo().with(IMPORTANCE, IMPORTANT).log("Finished MCTS test modules preparation.");
    } catch (MobileHarnessException e) {
      String errorMessage =
          "Failed to download Mainline CTS (MCTS). "
              + ((e.getErrorId() == AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE)
                  ? "At least one device is required to fetch Mainline info for download."
                      + " Please check your device availability."
                  : "Please check your network availability and the space of your host.");
      throw SkipTestException.create(
          errorMessage,
          DesiredTestResult.ERROR,
          AndroidErrorId.XTS_DYNAMIC_DOWNLOADER_FILE_NOT_FOUND,
          e);
    }
  }

  private boolean isStaticXtsJob(TestInfo testInfo) {
    return testInfo
        .jobInfo()
        .properties()
        .getOptional(XtsConstants.XTS_DYNAMIC_DOWNLOAD_JOB_NAME)
        .orElse("")
        .equals(XtsConstants.STATIC_XTS_JOB_NAME);
  }

  private String getDeviceId(LocalTestStartingEvent event)
      throws MobileHarnessException, InterruptedException {
    // Get all online devices first, then check against requested locators.
    Set<String> onlineDeviceSerials =
        adbInternalUtil.getDeviceSerialsByState(DeviceState.DEVICE, /* timeout= */ null);

    // Find the first online device of the allocation, or throw an exception if none are found.
    return event.getAllocation().getAllDeviceLocators().stream()
        .map(DeviceLocator::getSerial)
        .filter(onlineDeviceSerials::contains)
        .findFirst()
        .orElseThrow(
            () ->
                new MobileHarnessException(
                    AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE,
                    "No online device found for the current job."));
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

  /**
   * Gets the MCTS names of all mainline modules.
   *
   * <p>This method retrieves the list of preloaded mainline modules from the device and categorizes
   * them into preloaded and non-preloaded lists. The result is a ListMultimap where keys are {@link
   * #PRELOADED_KEY} and {@link #NON_PRELOADED_KEY}.
   *
   * <ul>
   *   <li>For {@link #PRELOADED_KEY}, the values are strings in the format "mctsName:versioncode".
   *   <li>For {@link #NON_PRELOADED_KEY}, the values are strings representing the mctsName.
   * </ul>
   *
   * <p>The return value is cached as part of the test properties. If a subsequent invocation of
   * this method encounters an exception (e.g., device goes offline), the cached value from the test
   * properties will be returned, allowing the plugin to proceed.
   *
   * @param event The test starting event, used to access test properties and device information.
   * @param aospVersion The AOSP version of the device.
   */
  private ListMultimap<String, String> getMctsNamesOfAllMainlineModules(
      LocalTestStartingEvent event, String aospVersion) throws MobileHarnessException {
    TestInfo testInfo = event.getTest();
    try {
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
      Set<String> preloadedModulesMctsAndVersioncode = new HashSet<>();
      Map<String, String> modulePackageToModuleInfoMap =
          moduleInfoMap.getModulePackageToModuleInfoMap();
      String deviceId = getDeviceId(event);
      ImmutableList<String> preloadedMainlineModules = getPreloadedMainlineModules(deviceId);
      boolean isAndroid14TvThread = false;
      if (aospVersion.equals("34")) {
        try {
          if (androidSystemSpecUtil
                  .getSystemFeatures(deviceId)
                  .contains("android.software.leanback")
              && androidSystemSpecUtil
                  .getSystemFeatures(deviceId)
                  .contains("android.hardware.thread_network")) {
            isAndroid14TvThread = true;
            logger.atInfo().log(
                "Android 14 TV with thread network feature detected, will use 2025-08 train for"
                    + " relevant modules.");
          }
        } catch (MobileHarnessException | InterruptedException e) {
          logger.atWarning().withCause(e).log(
              "Failed to check for TV/Thread features, proceeding without module overrides.");
        }
      }
      for (String moduleName : preloadedMainlineModules) {
        if (modulePackageToModuleInfoMap.containsKey(moduleName)) {
          String mctsName = modulePackageToModuleInfoMap.get(moduleName);
          if (preloadedModulesMcts.add(mctsName)) {
            String moduleVersion =
                Integer.toString(androidPackageManagerUtil.getAppVersionCode(deviceId, moduleName));
            // Only parse the module versioncode released start from Android V (35+).
            String moduleVersioncode =
                processModuleVersion(
                    moduleVersion, moduleName, MAINLINE_AOSP_VERSION_KEY, aospVersion);
            if (isAndroid14TvThread && ANDROID_14_TV_THREAD_MODULES.contains(mctsName)) {
              moduleVersioncode = "2025-08";
              logger.atInfo().log("Overriding version for %s to 2025-08", mctsName);
            }
            preloadedModulesMctsAndVersioncode.add(mctsName + ':' + moduleVersioncode);
          }
        }
      }
      // Put the preloaded modules on the preloaded list with the format: "mctsName:versioncode".
      mctsNamesOfAllModules.putAll(PRELOADED_KEY, preloadedModulesMctsAndVersioncode);
      // Put the non-preloaded modules on the non-preloaded list with the format: "mctsName".
      Set<String> nonPreloadMctsList = new HashSet<>();
      nonPreloadMctsList.addAll(modulePackageToModuleInfoMap.values());
      nonPreloadMctsList.removeAll(preloadedModulesMcts);
      mctsNamesOfAllModules.putAll(NON_PRELOADED_KEY, nonPreloadMctsList);

      // Save as part of the test properties.
      String serializedMctsModulesInfo =
          Base64.getEncoder().encodeToString(serialize((Serializable) mctsNamesOfAllModules));
      testInfo
          .properties()
          .add(XtsConstants.DEVICE_MCTS_MODULES_INFO_PROPERTY_KEY, serializedMctsModulesInfo);
      logger.atInfo().log("Read device MCTS modules info: %s", mctsNamesOfAllModules);
      return mctsNamesOfAllModules;
    } catch (MobileHarnessException | InterruptedException e) {
      logger.atInfo().withCause(e).log(
          "Failed to get device MCTS modules info. Will try to read from test properties.");
      return testInfo
          .properties()
          .getOptional(XtsConstants.DEVICE_MCTS_MODULES_INFO_PROPERTY_KEY)
          .map(
              serializedMctsModulesInfo -> {
                // Safe because we are deserializing data serialized from a
                // ListMultimap<String, String> in this class.
                @SuppressWarnings("unchecked")
                ListMultimap<String, String> mctsModulesInfo =
                    (ListMultimap<String, String>)
                        deserialize(Base64.getDecoder().decode(serializedMctsModulesInfo));
                logger.atInfo().log(
                    "Read device MCTS modules info from test properties: %s.", mctsModulesInfo);
                return mctsModulesInfo;
              })
          .orElseThrow(
              () ->
                  new MobileHarnessException(
                      AndroidErrorId.XTS_DYNAMIC_DOWNLOADER_DEVICE_INFO_NOT_FOUND,
                      "Did not get device MCTS modules info from test properties."));
    }
  }

  private String getPreloadedMainlineVersion(String versioncode, String moduleName)
      throws MobileHarnessException {
    // Get the release time of the preloaded mainline train, the format is YYYY-MM.
    // Note that version codes must always increase to successfully install newer builds. For this
    // reason, the version code "wraps" in January, making the month digits wrap to 13, instead of
    // 01 (for the first month of the year) and so on, if the month is 0 then it's aosp version.
    int month = Integer.parseInt(versioncode, 2, 4, 10);
    Integer sdkLevelYear = SDK_LEVEL_TO_YEAR.get(versioncode.substring(0, 2));
    if (sdkLevelYear == null) {
      throw new MobileHarnessException(
          AndroidErrorId.XTS_DYNAMIC_DOWNLOADER_DEVICE_SDK_VERSION_NOT_SUPPORT,
          "Device is not compatible with the xts dynamic downloader. Required R+ build.");
    }
    int year = sdkLevelYear + month / 12;
    String version = String.format("%d-%02d", year, (month % 12 == 0 ? 12 : month % 12));
    logger.atInfo().log("Get %s version(YYYY-MM): %s", moduleName, version);
    return version;
  }

  @Nullable
  String downloadPublicUrlFiles(String downloadUrl, String subDirName)
      throws MobileHarnessException, InterruptedException {
    synchronized (lock) {
      // e.g.
      // <xts_res_dir_root>/mcts_dynamic_download/android/xts/mcts/YYYY-MM/arm64/android-mcts-<module_name>.zip
      String dynamicDownloadDir =
          Flags.instance().xtsResDirRoot.getNonNull() + "/mcts_dynamic_download";
      String filePath = PathUtil.join(dynamicDownloadDir, subDirName);
      URLConnection connection = null;
      try {
        // get the last modified time of the url, will be 0 if the url does not exist.
        URI uri = new URI(downloadUrl);
        URL url = uri.toURL();
        connection = url.openConnection();
        long urlLastModified = connection.getLastModified();
        // check the file exists and the last modified time.
        if (urlLastModified == 0) {
          logger.atInfo().log("Url %s not exist.", downloadUrl);
          return null;
        } else if (fileUtil.isFileExist(filePath)) {
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
      TestInfo testInfo, String filePath, String subDirName, Set<String> excludeTestModules)
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
      if (!fileUtil.getFileOrDir(desPath + "/" + PathUtil.basename(path)).exists()
          && !excludeTestModules.contains(PathUtil.basename(path))) {
        fileUtil.moveFileOrDir(path, desPath);
        logger.atInfo().log("Moved test cases from link [%s] to [%s]", path, unzipDirPath);
        testModules.add(PathUtil.basename(path));
      }
    }
    logger.atInfo().log("Unzipped resource to %s", unzipDirPath);
    return testModules;
  }

  @FunctionalInterface
  private interface DeviceInfoSupplier {
    String get(String deviceId) throws MobileHarnessException, InterruptedException;
  }

  private String getOrFetchStringDeviceInfo(
      LocalTestStartingEvent event,
      String propertyKey,
      String propertyDisplayName,
      DeviceInfoSupplier supplier)
      throws MobileHarnessException {
    TestInfo testInfo = event.getTest();
    try {
      String deviceId = getDeviceId(event);
      String value = supplier.get(deviceId);
      testInfo.properties().add(propertyKey, value);
      logger.atInfo().log("Read device %s %s: %s", deviceId, propertyDisplayName, value);
      return value;
    } catch (MobileHarnessException | InterruptedException e) {
      logger.atInfo().withCause(e).log(
          "Failed to get device %s. Will try to read from test properties.", propertyDisplayName);
      return testInfo
          .properties()
          .getOptional(propertyKey)
          .map(
              value -> {
                logger.atInfo().log(
                    "Read device %s from test properties: %s", propertyDisplayName, value);
                return value;
              })
          .orElseThrow(
              () ->
                  new MobileHarnessException(
                      AndroidErrorId.XTS_DYNAMIC_DOWNLOADER_DEVICE_INFO_NOT_FOUND,
                      String.format(
                          "Did not get device %s from test properties.", propertyDisplayName)));
    }
  }

  /**
   * Gets the AOSP version (Android SDK level) of the device.
   *
   * <p>This method first attempts to fetch the SDK version directly from the device using ADB. If
   * successful, the value is cached in the test properties for future use. If a subsequent
   * invocation of this method encounters an exception (e.g., device goes offline), the cached value
   * from the test properties will be returned, allowing the plugin to proceed.
   *
   * @throws MobileHarnessException if the AOSP version cannot be fetched from the device and is not
   *     found in the test properties.
   */
  private String getAospVersion(LocalTestStartingEvent event) throws MobileHarnessException {
    return getOrFetchStringDeviceInfo(
        event,
        XtsConstants.DEVICE_AOSP_VERSION_PROPERTY_KEY,
        "AOSP version",
        (d) -> adbUtil.getProperty(d, AndroidProperty.SDK_VERSION));
  }

  /**
   * Gets the ABI version of the device.
   *
   * <p>This method first attempts to fetch the ABI version directly from the device using ADB. If
   * successful, the value is cached in the test properties for future use. If a subsequent
   * invocation of this method encounters an exception (e.g., device goes offline), the cached value
   * from the test properties will be returned, allowing the plugin to proceed.
   *
   * @throws MobileHarnessException if the ABI version cannot be fetched from the device and is not
   *     found in the test properties.
   */
  private String getAbiVersion(LocalTestStartingEvent event) throws MobileHarnessException {
    return getOrFetchStringDeviceInfo(
        event,
        XtsConstants.DEVICE_ABI_PROPERTY_KEY,
        "ABI version",
        (d) -> adbUtil.getProperty(d, AndroidProperty.ABI));
  }

  private String processModuleVersion(
      String moduleVersionNumber, String moduleName, String defaultVersion, String aospVersion)
      throws MobileHarnessException {
    if (VERSIONCODE_PATTERN.matcher(moduleVersionNumber).matches()
        && !PLATFORM_BETA_VERSIONCODE_PATTERN.matcher(moduleVersionNumber).matches()
        && !AOSP_VERSIONCODE_LIST.contains(moduleVersionNumber)) {
      String preloadVersion = getPreloadedMainlineVersion(moduleVersionNumber, moduleName);
      ImmutableList<String> versionCodes = INITIAL_RELEASE_VERSIONCODE_MAP.get(aospVersion);
      if (versionCodes != null && versionCodes.contains(preloadVersion)) {
        logger.atInfo().log(
            "The train version %s is Android new SDK initial release", preloadVersion);
        return defaultVersion;
      }
      return preloadVersion; // Return preloadVersion if aospVersion key is not found, or
      // preloadVersion is not in the list.
    } else {
      return defaultVersion;
    }
  }

  /**
   * Gets the Train Version Package (TVP) version from the device.
   *
   * <p>This method first attempts to fetch the TVP version directly from the device using ADB. If
   * successful, the value is cached in the test properties for future use. If a subsequent
   * invocation of this method encounters an exception (e.g., device goes offline), the cached value
   * from the test properties will be returned, allowing the plugin to proceed.
   *
   * @throws MobileHarnessException if the TVP version cannot be fetched from the device and is not
   *     found in the test properties.
   */
  private String getTvpVersion(LocalTestStartingEvent event) throws MobileHarnessException {
    return getOrFetchStringDeviceInfo(
        event,
        XtsConstants.DEVICE_TVP_VERSION_PROPERTY_KEY,
        "TVP version",
        (d) -> {
          ImmutableList<String> preloadedMainlineModules = getPreloadedMainlineModules(d);
          // if the TVP version is 310000000, that means all the mainline modules were built
          // from source, rather than prebuilt dropped. 310000000 is just the default value in
          // http://ac/vendor/unbundled_google/modules/ModuleMetadataGoogle/Primary_AndroidManifest.xml
          // We will only support Android V train for downloading train MCTS, otherwise will
          // download
          // from aosp.
          return preloadedMainlineModules.contains(MAINLINE_TVP_PKG)
              ? Integer.toString(androidPackageManagerUtil.getAppVersionCode(d, MAINLINE_TVP_PKG))
              : "310000000";
        });
  }
}
