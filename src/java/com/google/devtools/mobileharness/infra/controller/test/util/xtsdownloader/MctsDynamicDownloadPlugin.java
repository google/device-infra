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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Built in lab plugin for MCTS test suites dynamic downloading. */
public class MctsDynamicDownloadPlugin implements XtsDynamicDownloadPluginInterface {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Object lock = new Object();

  private static final String STATIC_MCTS_TESTCASES_PATH = "/android/xts/mcts";

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

  public MctsDynamicDownloadPlugin() {
    this.adbUtil = new AndroidAdbUtil();
    this.fileUtil = new LocalFileUtil();
    this.androidPackageManagerUtil = new AndroidPackageManagerUtil();
  }

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
    ImmutableList<String> mctsNamesOfPreloadedMainlineModules =
        getMctsNamesOfPreloadedMainlineModules(preloadedMainlineModules);
    String preloadedMainlineVersion = getPreloadedMainlineVersion(device);
    String deviceAbi =
        DEVICEABI_MAP.get(adbUtil.getProperty(device.getDeviceId(), AndroidProperty.ABI));
    if (deviceAbi == null) {
      throw new MobileHarnessException(
          AndroidErrorId.XTS_DYNAMIC_DOWNLOADER_DEVICE_ABI_NOT_SUPPORT,
          String.format(
              "The ABI of device %s is not compatible with the xts dynamic downloader.",
              device.getDeviceId()));
    }
    // Update the Lorry download link url of MCTS file. For example:
    // https://dl.google.com/dl/android/xts/mcts/YYYY-MM/arm64/android-mcts-<module_name>.zip
    List<String> downloadLinkUrls = new ArrayList<>();
    for (String mctsName : mctsNamesOfPreloadedMainlineModules) {
      String downloadUrl =
          String.format(
              "https://dl.google.com/dl/android/xts/mcts/%s/%s/%s.zip",
              "test_only", deviceAbi, mctsName); // test only, need update!!!
      downloadLinkUrls.add(downloadUrl);
    }
    return XtsDynamicDownloadInfo.newBuilder()
        .setXtsType("cts")
        .setProject(XtsDynamicDownloadInfo.Project.MAINLINE)
        .addAllDownloadUrl(downloadLinkUrls)
        .addDownloadUrl(
            String.format(
                "https://dl.google.com/dl/android/xts/mcts/%s/%s/%s.zip",
                "test_only", deviceAbi, "android-mcts-mainline-infra"))
        .build();
  }

  @Override
  public void downloadXtsFiles(XtsDynamicDownloadInfo xtsDynamicDownloadInfo, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    for (String downloadUrl : xtsDynamicDownloadInfo.getDownloadUrlList()) {
      logger.atInfo().log("Start to download: %s", downloadUrl);
      String subDirName = downloadUrl.replace("https://dl.google.com/dl", "");
      String filePath = downloadPublicUrlFiles(downloadUrl, subDirName);
      unzipDownloadedTestCases(testInfo, filePath, subDirName);
    }
  }

  @Subscribe
  void onDriverStarting(LocalDriverStartingEvent event)
      throws MobileHarnessException, InterruptedException {
    if (!event.getDriverName().equals("XtsTradefedTest")) {
      return;
    }
    XtsDynamicDownloadInfo xtsDynamicDownloadInfo = parse(event.getTest(), event.getDevice());
    downloadXtsFiles(xtsDynamicDownloadInfo, event.getTest());
  }

  private ImmutableList<String> getPreloadedMainlineModules(Device device)
      throws MobileHarnessException, InterruptedException {
    return androidPackageManagerUtil.listModuleInfos(device.getDeviceId()).stream()
        .map(ModuleInfo::packageName)
        .collect(toImmutableList());
  }

  private ImmutableList<String> getMctsNamesOfPreloadedMainlineModules(
      ImmutableList<String> moduleList) throws MobileHarnessException {
    ModuleInfoMap moduleInfoMap;
    try (InputStreamReader configInputReader =
        new InputStreamReader(
            MctsDynamicDownloadPlugin.class.getResourceAsStream("configs/module_info_map.textpb"),
            UTF_8)) {
      ModuleInfoMap.Builder moduleInfoMapBuilder = ModuleInfoMap.newBuilder();
      TextFormat.merge(configInputReader, moduleInfoMapBuilder);
      moduleInfoMap = moduleInfoMapBuilder.build();
    } catch (IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.XTS_DYNAMIC_DOWNLOADER_CONFIG_READER_ERROR, e.getMessage(), e);
    }
    return moduleInfoMap.getModulePackageToModuleInfoMap().entrySet().stream()
        .filter(entry -> moduleList.contains(entry.getKey()))
        .map(Map.Entry::getValue)
        .collect(toImmutableList());
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

  private void unzipDownloadedTestCases(TestInfo testInfo, String filePath, String subDirName)
      throws MobileHarnessException, InterruptedException {
    if (filePath == null) {
      return;
    }
    // unzip the file to /tmp/android/xts/mcts/android-mcts-<module>/testcases
    String unzipDirPath = testInfo.getTmpFileDir() + STATIC_MCTS_TESTCASES_PATH;
    fileUtil.unzipFile(filePath, unzipDirPath);
    // mv all the mcts test cases to /tmp/android/xts/mcts/testcases/
    List<String> listPaths =
        fileUtil.listFileOrDirPaths(
            unzipDirPath
                + "/"
                + subDirName.substring(subDirName.lastIndexOf("/") + 1).replace(".zip", "")
                + "/testcases");
    for (String path : listPaths) {
      fileUtil.moveFileOrDir(path, unzipDirPath + "/testcases");
      logger.atInfo().log("Moved test cases from link [%s] to [%s]", path, unzipDirPath);
    }
    logger.atInfo().log("Unzipped resource to %s", unzipDirPath);
  }
}
