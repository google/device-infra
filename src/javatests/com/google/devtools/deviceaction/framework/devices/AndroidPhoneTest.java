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

package com.google.devtools.deviceaction.framework.devices;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.utils.BundletoolUtil;
import com.google.devtools.deviceaction.framework.proto.AndroidPhoneSpec;
import com.google.devtools.deviceaction.framework.proto.DeviceType;
import com.google.devtools.deviceinfra.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.ModuleInfo;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageInfo;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.protobuf.util.Durations;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(TestParameterInjector.class)
public class AndroidPhoneTest {
  private static final long VERSION_CODE = 123L;
  private static final String PACKAGE_NAME = "fake.package";
  private static final String SOURCE_DIR = "/data/app/fake";
  private static final String FAKE_MODULE = "fake module";
  private static final String SDK_VERSION = "31";
  private static final String UUID = "serial";

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Captor public ArgumentCaptor<Multimap<String, String>> multimapArgumentCaptor;

  @Mock private AndroidAdbUtil mockAdbUtil;
  @Mock private AndroidFileUtil mockFileUtil;
  @Mock private AndroidPackageManagerUtil mockPackageManagerUtil;
  @Mock private AndroidSystemSettingUtil mockSystemSettingUtil;
  @Mock private AndroidSystemStateUtil mockSystemStateUtil;
  @Mock private BundletoolUtil mockBundletoolUtil;

  enum AndroidPhoneSpecTestCase {
    DEFAULT(AndroidPhoneSpec.newBuilder().setBrand("google").build()),
    STAGE(
        AndroidPhoneSpec.newBuilder()
            .setBrand("google")
            .setStagedReadyTimeout(Durations.fromMinutes(2))
            .setExtraWaitForStaging(Durations.fromMinutes(1))
            .build()),
    REBOOT(
        AndroidPhoneSpec.newBuilder()
            .setBrand("Google")
            .setRebootAwait(Durations.fromSeconds(10))
            .setRebootTimeout(Durations.fromMinutes(3))
            .build()),
    TESTHARNESS(
        AndroidPhoneSpec.newBuilder()
            .setBrand("GOOGLE")
            .setTestharnessBootAwait(Durations.fromSeconds(10))
            .setTestharnessBootTimeout(Durations.fromMinutes(3))
            .build());

    final AndroidPhoneSpec androidPhoneSpec;

    AndroidPhoneSpecTestCase(AndroidPhoneSpec androidPhoneSpec) {
      this.androidPhoneSpec = androidPhoneSpec;
    }
  }

  @TestParameter({"DEFAULT", "STAGE", "REBOOT", "TESTHARNESS"})
  private AndroidPhoneSpecTestCase specTestCase;

  private AndroidPhoneSpec spec;

  private AndroidPhone device;
  private final Sleeper sleeper = Sleeper.noOpSleeper();
  private final PackageInfo packageInfo =
      PackageInfo.builder()
          .setPackageName(PACKAGE_NAME)
          .setSourceDir(SOURCE_DIR)
          .setVersionCode(VERSION_CODE)
          .build();

  @Before
  public void setup() throws Exception {
    when(mockAdbUtil.getProperty(UUID, AndroidProperty.SDK_VERSION)).thenReturn(SDK_VERSION);
    spec = specTestCase.androidPhoneSpec;
    device =
        new AndroidPhone(
            mockAdbUtil,
            mockFileUtil,
            mockPackageManagerUtil,
            mockSystemSettingUtil,
            mockSystemStateUtil,
            mockBundletoolUtil,
            sleeper,
            UUID,
            spec);
  }

  @Test
  public void getUuid_getExpected() {
    assertThat(device.getUuid()).isEqualTo(UUID);
  }

  @Test
  public void getDeviceType_getExpected() {
    assertThat(device.getDeviceType()).isEqualTo(DeviceType.ANDROID_PHONE);
  }

  @Test
  public void listPackages_getExpected() throws Exception {
    SortedSet<PackageInfo> expected = new TreeSet<>();
    expected.add(packageInfo);
    when(mockPackageManagerUtil.listPackageInfos(UUID)).thenReturn(expected);

    assertThat(device.listPackages()).isEqualTo(expected);
  }

  @Test
  public void listPackages_catchMobileHarnessException_throwException() throws Exception {
    when(mockPackageManagerUtil.listPackageInfos(UUID)).thenThrow(fakeMobileHarnessException());

    assertThrows(DeviceActionException.class, () -> device.listPackages());
  }

  @Test
  public void listApexPackages_getExpected() throws Exception {
    SortedSet<PackageInfo> expected = new TreeSet<>();
    expected.add(packageInfo);
    when(mockPackageManagerUtil.listApexPackageInfos(UUID)).thenReturn(expected);

    assertThat(device.listApexPackages()).isEqualTo(expected);
  }

  @Test
  public void listApexPackages_catchMobileHarnessException_throwException() throws Exception {
    when(mockPackageManagerUtil.listApexPackageInfos(UUID)).thenThrow(fakeMobileHarnessException());

    assertThrows(DeviceActionException.class, () -> device.listApexPackages());
  }

  @Test
  public void listModules_getExpected() throws Exception {
    SortedSet<ModuleInfo> expected = new TreeSet<>();
    expected.add(ModuleInfo.builder().setPackageName(PACKAGE_NAME).setName(FAKE_MODULE).build());
    when(mockPackageManagerUtil.listModuleInfos(UUID)).thenReturn(expected);

    assertThat(device.listModules()).isEqualTo(expected);
  }

  @Test
  public void listModules_catchMobileHarnessException_throwException() throws Exception {
    when(mockPackageManagerUtil.listModuleInfos(UUID)).thenThrow(fakeMobileHarnessException());

    assertThrows(DeviceActionException.class, () -> device.listModules());
  }

  @Test
  public void getDeviceSpecFilePath_cacheResults_getExpected() throws Exception {
    String expect = "/file.json";
    when(mockBundletoolUtil.generateDeviceSpecFile(UUID)).thenReturn(Paths.get(expect));

    assertThat(device.getDeviceSpecFilePath().toString()).isEqualTo(expect);
    assertThat(device.getDeviceSpecFilePath().toString()).isEqualTo(expect);
    verify(mockBundletoolUtil).generateDeviceSpecFile(UUID);
  }

  @Test
  public void getDeviceSpecFilePath_throwExceptionFromBundletoolUtil() throws Exception {
    when(mockBundletoolUtil.generateDeviceSpecFile(UUID)).thenThrow(fakeDeviceActionException());

    assertThrows(DeviceActionException.class, () -> device.getDeviceSpecFilePath());
  }

  @Test
  public void getSdkVersion_cacheResults_getExpected() throws Exception {
    int sdkVersion = device.getSdkVersion();
    int sdkVersion1 = device.getSdkVersion();

    assertThat(sdkVersion).isEqualTo(31);
    assertThat(sdkVersion1).isEqualTo(31);
    verify(mockAdbUtil).getProperty(anyString(), any(AndroidProperty.class));
  }

  @Test
  public void getSdkVersion_catchMobileHarnessException_throwException() throws Exception {
    when(mockAdbUtil.getProperty(UUID, AndroidProperty.SDK_VERSION))
        .thenThrow(fakeMobileHarnessException());

    assertThrows(DeviceActionException.class, () -> device.getSdkVersion());
  }

  @Test
  public void removeFiles_success() throws Exception {
    device.removeFiles("/data/system/package_cache/");

    verify(mockFileUtil).removeFiles(UUID, "/data/system/package_cache/");
  }

  @Test
  public void removeFiles_catchMobileHarnessException_throwException() throws Exception {
    doThrow(fakeMobileHarnessException()).when(mockFileUtil).removeFiles(anyString(), anyString());

    assertThrows(
        DeviceActionException.class, () -> device.removeFiles("/data/system/package_cache/"));
  }

  @Test
  public void listFiles_getExpected() throws Exception {
    String filePath = "/path/to/directory";
    String file1 = "/path/to/directory/file1";
    String file2 = "/path/to/directory/file2";
    SortedSet<String> expect = new TreeSet<>();
    expect.add(file1);
    expect.add(file2);
    when(mockFileUtil.listFilesInOrder(UUID, filePath)).thenReturn(expect);

    SortedSet<String> files = device.listFiles(filePath);

    assertThat(files).containsExactly(file1, file2);
    verify(mockFileUtil).listFilesInOrder(UUID, filePath);
  }

  @Test
  public void listFiles_catchMobileHarnessException_throwException() throws Exception {
    String filePath = "/path/to/directory";
    when(mockFileUtil.listFilesInOrder(UUID, filePath)).thenThrow(fakeMobileHarnessException());

    assertThrows(DeviceActionException.class, () -> device.listFiles(filePath));
  }

  @Test
  public void getAllInstalledPaths_getExpected() throws Exception {
    ImmutableList<String> paths =
        ImmutableList.of("/data/app/com.example.app/file1", "/data/app/com.example.app/file2");
    when(mockPackageManagerUtil.getAllInstalledPaths(any(), eq(PACKAGE_NAME))).thenReturn(paths);
    ArgumentCaptor<UtilArgs> utilArgsCaptor = ArgumentCaptor.forClass(UtilArgs.class);

    ImmutableList<String> allInstalledPath = device.getAllInstalledPaths(PACKAGE_NAME);
    assertThat(allInstalledPath)
        .containsExactly("/data/app/com.example.app/file1", "/data/app/com.example.app/file2");
    verify(mockPackageManagerUtil).getAllInstalledPaths(utilArgsCaptor.capture(), eq(PACKAGE_NAME));
    assertThat(utilArgsCaptor.getValue().serial()).isEqualTo(UUID);
  }

  @Test
  public void getAllInstalledPaths_catchMobileHarnessException_throwException() throws Exception {
    when(mockPackageManagerUtil.getAllInstalledPaths(any(), eq(PACKAGE_NAME)))
        .thenThrow(fakeMobileHarnessException());

    assertThrows(DeviceActionException.class, () -> device.getAllInstalledPaths(PACKAGE_NAME));
  }

  @Test
  public void becomeRoot_callSystemStateUtil() throws Exception {
    device.becomeRoot();

    verify(mockSystemStateUtil).becomeRoot(UUID);
  }

  @Test
  public void becomeRoot_catchMobileHarnessException_throwException() throws Exception {
    doThrow(fakeMobileHarnessException()).when(mockSystemStateUtil).becomeRoot(UUID);

    assertThrows(DeviceActionException.class, () -> device.becomeRoot());
  }

  @Test
  public void push_success() throws Exception {
    Path srcOnHost = Paths.get("src/main/java/com/example/app/MyClass.java");
    Path desOnDevice = Path.of("/data/local/tmp/MyClass.java");

    device.push(srcOnHost, desOnDevice);

    verify(mockFileUtil).push(UUID, 31, srcOnHost.toString(), desOnDevice.toString());
  }

  @Test
  public void push_catchMobileHarnessException_throwException() throws Exception {
    Path srcOnHost = Paths.get("src/main/java/com/example/app/MyClass.java");
    Path desOnDevice = Path.of("/data/local/tmp/MyClass.java");
    doThrow(fakeMobileHarnessException())
        .when(mockFileUtil)
        .push(eq(UUID), eq(31), anyString(), anyString());

    assertThrows(DeviceActionException.class, () -> device.push(srcOnHost, desOnDevice));
  }

  @Test
  public void remount_success() throws Exception {
    device.remount();

    verify(mockSystemStateUtil).becomeRoot(UUID);
    verify(mockFileUtil).remount(UUID, true);
  }

  @Test
  public void remount_catchMobileHarnessException_throwException() throws Exception {
    doThrow(fakeMobileHarnessException()).when(mockFileUtil).remount(UUID, true);

    assertThrows(DeviceActionException.class, () -> device.remount());
  }

  @Test
  @TestParameters("{sign: dev-keys, expectedReturn: true}")
  @TestParameters("{sign: release-keys, expectedReturn: false}")
  public void devKeySigned_getExpectedValue(String sign, boolean expectedReturn) throws Exception {
    when(mockAdbUtil.getProperty(UUID, AndroidProperty.SIGN)).thenReturn(sign);

    boolean actural = device.devKeySigned();

    assertThat(actural).isEqualTo(expectedReturn);
  }

  private static MobileHarnessException fakeMobileHarnessException() {
    return new MobileHarnessException(BasicErrorId.SYSTEM_INVALID_PROCESS_ID, "message");
  }

  private static DeviceActionException fakeDeviceActionException() {
    return new DeviceActionException("FAKE", ErrorType.CUSTOMER_ISSUE, "message");
  }
}
