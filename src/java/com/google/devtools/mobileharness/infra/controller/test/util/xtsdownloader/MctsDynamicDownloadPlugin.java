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
import com.google.devtools.mobileharness.api.testrunner.event.test.LocalDriverStartingEvent;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.ModuleInfo;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.protobuf.TextFormat;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/** Built in lab plugin for MCTS test suites dynamic downloading. */
public class MctsDynamicDownloadPlugin implements XtsDynamicDownloadPlugin {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Object lock = new Object();

  private static final String STATIC_MCTS_TESTCASES_PATH = "/android/xts/mcts";

  private static final String TMP_MCTS_TESTCASES_PATH = "/android/xts/mcts/testcases";

  private static final String XTS_DYNAMIC_DOWNLOAD_PATH_KEY = "xts_dynamic_download_path";

  private static final ImmutableMap<String, Integer> SDKLEVEL_TO_YEAR =
      ImmutableMap.of(
          "30", 2020,
          "31", 2021,
          "32", 2022,
          "33", 2022,
          "34", 2023);
  private static final ImmutableMap<String, String> DEVICEABI_MAP =
      ImmutableMap.of(
          "armeabi", "arm",
          "armeabi-v7a", "arm",
          "armeabi-v7a-hard", "arm",
          "arm64-v8a", "arm64",
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
  public XtsDynamicDownloadInfo parse(TestInfo test, Device device)
      throws MobileHarnessException, InterruptedException {
    ImmutableList<String> preloadedMainlineModules = getPreloadedMainlineModules(device);
    ListMultimap<String, String> mctsNamesOfPreloadedMainlineModules =
        getMctsNamesOfPreloadedMainlineModules(preloadedMainlineModules);
    String deviceAbi =
        DEVICEABI_MAP.get(adbUtil.getProperty(device.getDeviceId(), AndroidProperty.ABI));
    if (deviceAbi == null) {
      throw new MobileHarnessException(
          AndroidErrorId.XTS_DYNAMIC_DOWNLOADER_DEVICE_ABI_NOT_SUPPORT,
          String.format(
              "The ABI of device %s is not compatible with the xts dynamic downloader.",
              device.getDeviceId()));
    }
    String aospVersion = adbUtil.getProperty(device.getDeviceId(), AndroidProperty.SDK_VERSION);
    List<String> downloadLinkUrls = new ArrayList<>();
    // Add the Lorry download link url of MCTS file for preloaded mainline modules. For example:
    // https://dl.google.com/dl/android/xts/mcts/YYYY-MM/arm64/android-mcts-<module_name>.zip
    if (!preloadedMainlineModules.isEmpty()) {
      String preloadedMainlineVersion = getPreloadedMainlineVersion(device);
      for (String mctsName : mctsNamesOfPreloadedMainlineModules.get("preloaded")) {
        String downloadUrl =
            String.format(
                "https://dl.google.com/dl/android/xts/mcts/%s/%s/%s.zip",
                preloadedMainlineVersion, deviceAbi, mctsName);
        downloadLinkUrls.add(downloadUrl);
      }
    }
    // Add the Lorry download link url of MCTS file for non-preloaded mainline modules. For example:
    // https://dl.google.com/dl/android/xts/mcts/{SDK_VERSION}/arm64/android-mcts-<module_name>.zip
    for (String mctsName : mctsNamesOfPreloadedMainlineModules.get("non-preloaded")) {
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
    testInfo.properties().add(XTS_DYNAMIC_DOWNLOAD_PATH_KEY, TMP_MCTS_TESTCASES_PATH);
    // Print out all the downloaded MCTS test modules
    logger.atInfo().log("Downloaded MCTS test modules:");
    for (String testModule : allTestModules) {
      logger.atInfo().log("%s", testModule);
    }
  }

  @Subscribe
  void onDriverStarting(LocalDriverStartingEvent event)
      throws MobileHarnessException, InterruptedException {
    if (!event.getDriverName().equals("XtsTradefedTest")) {
      return;
    }
    // TODO: add a check here to block using multi devices from different models.
    XtsDynamicDownloadInfo xtsDynamicDownloadInfo = parse(event.getTest(), event.getDevice());
    downloadXtsFiles(xtsDynamicDownloadInfo, event.getTest());
  }

  private ImmutableList<String> getPreloadedMainlineModules(Device device)
      throws InterruptedException {
    try {
      return androidPackageManagerUtil.listModuleInfos(device.getDeviceId()).stream()
          .map(ModuleInfo::packageName)
          .collect(toImmutableList());
    } catch (MobileHarnessException e) {
      logger.atInfo().log(
          "Cannot get preloaded moduleinfo, handle this exception since this device might be built"
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
    Set<String> preloadedModules = new HashSet<>(); // Track modules added to 'preloaded'
    moduleInfoMap
        .getModulePackageToModuleInfoMap()
        .entrySet()
        .forEach(
            entry -> {
              String moduleName = entry.getKey();
              String mctsName = entry.getValue();

              if (moduleList.contains(moduleName)) {
                mctsNamesOfAllModules.put("preloaded", mctsName);
                preloadedModules.add(mctsName);
              } else {
                mctsNamesOfAllModules.put("non-preloaded", mctsName);
              }

              // If a module is later found in 'preloaded', remove it from 'non-preloaded'
              if (preloadedModules.contains(mctsName)) {
                mctsNamesOfAllModules.remove("non-preloaded", mctsName);
              }
            });
    return mctsNamesOfAllModules;
  }

  private String getPreloadedMainlineVersion(Device device)
      throws MobileHarnessException, InterruptedException {
    String versioncode =
        Integer.toString(
            androidPackageManagerUtil.getAppVersionCode(
                device.getDeviceId(), "com.google.android.modulemetadata"));
    // Get the release time of the preloaded mainline train, the format is YYYY-MM.
    // Note that version codes must always increase to successfully install newer builds. For this
    // reason, the version code "wraps" in January, making the month digits wrap to 13, instead of
    // 01 (for the first month of the year) and so on.
    int month = Integer.parseInt(versioncode, 2, 4, 10);
    if (!SDKLEVEL_TO_YEAR.containsKey(versioncode.substring(0, 2))) {
      throw new MobileHarnessException(
          AndroidErrorId.XTS_DYNAMIC_DOWNLOADER_DEVICE_SDK_VERSION_NOT_SUPPORT,
          "Device is not compatible with the xts dynamic downloader. Required R+ build.");
    }
    int year = SDKLEVEL_TO_YEAR.get(versioncode.substring(0, 2)) + month / 12;
    String version = String.format("%d-%02d", year, (month % 12 == 0 ? 12 : month % 12));
    logger.atInfo().log("Get mainline train version(YYYY-MM): %s", version);
    return version;
  }

  @Nullable
  String downloadPublicUrlFiles(String downloadUrl, String subDirName)
      throws MobileHarnessException {
    synchronized (lock) {
      // mh_res_files/android/xts/mcts/YYYY-MM/arm64/android-mcts-<module_name>.zip
      String filePath = PathUtil.join(ResUtil.getResDir(), subDirName);
      try {
        // TODO: Add a file checker.
        fileUtil.checkFile(filePath);
        logger.atInfo().log("Resource %s is already downloaded to %s", downloadUrl, filePath);
        return filePath;
      } catch (MobileHarnessException e) {
        logger.atInfo().log(
            "File %s does not exist, needs to download the file %s.", downloadUrl, filePath);
      }
      URLConnection connection = null;
      try {
        URL url = new URL(downloadUrl);
        connection = url.openConnection();
      } catch (IOException e) {
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
          FileOutputStream outputStream = new FileOutputStream(filePath); ) {
        if (inputStream == null) {
          throw new MobileHarnessException(
              AndroidErrorId.XTS_DYNAMIC_DOWNLOADER_FILE_NOT_FOUND,
              "Can not find resource " + downloadUrl);
        }
        ByteStreams.copy(inputStream, outputStream);
        fileUtil.grantFileOrDirFullAccess(ResUtil.getResDir());
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
