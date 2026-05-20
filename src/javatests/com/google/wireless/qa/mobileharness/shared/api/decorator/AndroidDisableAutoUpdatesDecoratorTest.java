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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.IntentArgs;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.xml.sax.SAXParseException;

@RunWith(JUnit4.class)
public class AndroidDisableAutoUpdatesDecoratorTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private AndroidDevice device;
  @Mock private Driver decoratedDriver;
  @Mock private LocalFileUtil localFileUtil;
  @Mock private AndroidFileUtil androidFileUtil;
  @Mock private Adb adb;
  @Mock private AndroidAdbUtil adbUtil;
  @Mock private AndroidSystemSettingUtil systemSettingUtil;
  private TestInfo testInfo;
  private JobInfo jobInfo;

  // Constants for Android Phones, at least through O. If the location changes in the future, these
  // should remain, to test decorator is still backwards compatible.
  private static final String FINSKY_CONFIG_FILE =
      "/data/data/com.android.vending/shared_prefs/finsky.xml";
  private static final String FINSKY_CONFIG_NAME = "auto_update_enabled";
  private static final String FINSKY_CONFIG_VALUE_DISABLE = "false";
  private static final String FINSKY_CONFIG_VALUE_ENABLE = "true";
  private static final String VENDING_CONFIG_FILE =
      "/data/data/com.android.vending/shared_prefs/com.android.vending_preferences.xml";
  private static final String VENDING_CONFIG_NAME = "auto-update-mode";
  private static final String VENDING_CONFIG_VALUE_DISABLE = "AUTO_UPDATE_NEVER";
  private static final String VENDING_CONFIG_VALUE_ENABLE = "AUTO_UPDATE_WIFI";
  private static final String GSERVICES_OVERRIDE_ACTION =
      "com.google.gservices.intent.action.GSERVICES_OVERRIDE";
  private static final String STRING_TYPE = "string";
  private static final String BOOL_TYPE = "boolean";
  private static final String BLANK_CONFIG =
      "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?><map></map>";
  private static final String NAME = "config_name";
  private static final String VALUE = "changedValue";
  private static final String DEVICE_ID = "12345654321";
  private static final String FINSKY_FILE_CONTENTS = "finsky_file_contents";
  private static final String VENDING_FILE_CONTENTS = "vending_file_contents";
  private static final String FINKSY_CONFIG_CONTENTS = "finksy_config_contents";
  private static final String VENDING_CONFIG_CONTENTS = "vending_config_contents";
  private static final String CONFIG_FILE_PATH = "/some/path/before.sh";
  private static final int SDK_VERSION = 30;

  @Before
  public void setUp() throws Exception {
    String rootTmpDir = temporaryFolder.newFolder().toString();
    JobSetting jobSetting =
        JobSetting.newBuilder().setTmpFileDir(PathUtil.join(rootTmpDir, "tmpFileDir")).build();
    jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_name"))
            .setType(JobType.getDefaultInstance())
            .setSetting(jobSetting)
            .build();
    jobInfo.tests().add("test_name");
    testInfo = jobInfo.tests().getByName("test_name").get(0);
    when(decoratedDriver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(systemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(SDK_VERSION);
  }

  @Test
  public void addOrUpdateConfigValueInConfigContents_newConfigFile() throws Exception {
    AndroidDisableAutoUpdatesDecorator decorator =
        new AndroidDisableAutoUpdatesDecorator(
            decoratedDriver,
            testInfo,
            localFileUtil,
            androidFileUtil,
            adb,
            adbUtil,
            systemSettingUtil);

    String contents =
        decorator.addOrUpdateConfigValueInConfigContents(BLANK_CONFIG, STRING_TYPE, NAME, VALUE);

    assertThat(contents).isEqualTo(settingsContent(getStringTag(NAME, VALUE)));
  }

  @Test
  public void addOrUpdateConfigValueInConfigContents_newConfigFileBoolType() throws Exception {
    AndroidDisableAutoUpdatesDecorator decorator =
        new AndroidDisableAutoUpdatesDecorator(
            decoratedDriver,
            testInfo,
            localFileUtil,
            androidFileUtil,
            adb,
            adbUtil,
            systemSettingUtil);

    String contents =
        decorator.addOrUpdateConfigValueInConfigContents(BLANK_CONFIG, BOOL_TYPE, NAME, VALUE);

    assertThat(contents).isEqualTo(settingsContent(getNonStringTag(BOOL_TYPE, NAME, VALUE)));
  }

  @Test
  public void addOrUpdateConfigValueInConfigContents_settingDoesNotExist() throws Exception {
    AndroidDisableAutoUpdatesDecorator decorator =
        new AndroidDisableAutoUpdatesDecorator(
            decoratedDriver,
            testInfo,
            localFileUtil,
            androidFileUtil,
            adb,
            adbUtil,
            systemSettingUtil);
    String fileContents =
        settingsContent(
            getStringTag("name1", "value1") + getNonStringTag("type2", "name2", "val2"));

    String contents =
        decorator.addOrUpdateConfigValueInConfigContents(fileContents, BOOL_TYPE, NAME, VALUE);

    assertThat(contents)
        .isEqualTo(
            settingsContent(
                getStringTag("name1", "value1")
                    + getNonStringTag("type2", "name2", "val2")
                    + getNonStringTag(BOOL_TYPE, NAME, VALUE)));
  }

  @Test
  public void addOrUpdateConfigValueInConfigContents_stringSettingExists() throws Exception {
    AndroidDisableAutoUpdatesDecorator decorator =
        new AndroidDisableAutoUpdatesDecorator(
            decoratedDriver,
            testInfo,
            localFileUtil,
            androidFileUtil,
            adb,
            adbUtil,
            systemSettingUtil);
    String fileContents =
        settingsContent(
            getStringTag("name1", "value1")
                + getStringTag(NAME, "originalValue")
                + getNonStringTag("type2", "name2", "val2"));

    String contents =
        decorator.addOrUpdateConfigValueInConfigContents(fileContents, STRING_TYPE, NAME, VALUE);

    assertThat(contents)
        .isEqualTo(
            settingsContent(
                getStringTag("name1", "value1")
                    + getStringTag(NAME, VALUE)
                    + getNonStringTag("type2", "name2", "val2")));
  }

  @Test
  public void addOrUpdateConfigValueInConfigContents_nonStringSettingExists() throws Exception {
    AndroidDisableAutoUpdatesDecorator decorator =
        new AndroidDisableAutoUpdatesDecorator(
            decoratedDriver,
            testInfo,
            localFileUtil,
            androidFileUtil,
            adb,
            adbUtil,
            systemSettingUtil);
    String fileContents =
        settingsContent(
            getStringTag("name1", "value1")
                + getNonStringTag(BOOL_TYPE, NAME, "originalValue")
                + getNonStringTag("type2", "name2", "val2"));

    String contents =
        decorator.addOrUpdateConfigValueInConfigContents(fileContents, BOOL_TYPE, NAME, VALUE);

    assertThat(contents)
        .isEqualTo(
            settingsContent(
                getStringTag("name1", "value1")
                    + getNonStringTag(BOOL_TYPE, NAME, VALUE)
                    + getNonStringTag("type2", "name2", "val2")));
  }

  @Test
  public void testDefaultRun() throws MobileHarnessException, InterruptedException, IOException {
    AndroidDisableAutoUpdatesDecorator decorator =
        spy(
            new AndroidDisableAutoUpdatesDecorator(
                decoratedDriver,
                testInfo,
                localFileUtil,
                androidFileUtil,
                adb,
                adbUtil,
                systemSettingUtil));
    jobInfo.params().add(AndroidDisableAutoUpdatesDecorator.PARAM_ENABLE_UPDATES, "false");

    when(device.isRooted()).thenReturn(true);
    doReturn(FINSKY_FILE_CONTENTS)
        .when(decorator)
        .getDeviceFileContentsOrBlankDoc(DEVICE_ID, FINSKY_CONFIG_FILE);
    doReturn(VENDING_FILE_CONTENTS)
        .when(decorator)
        .getDeviceFileContentsOrBlankDoc(DEVICE_ID, VENDING_CONFIG_FILE);

    doReturn(FINKSY_CONFIG_CONTENTS)
        .when(decorator)
        .addOrUpdateConfigValueInConfigContents(
            FINSKY_FILE_CONTENTS, BOOL_TYPE, FINSKY_CONFIG_NAME, FINSKY_CONFIG_VALUE_DISABLE);
    doReturn(VENDING_CONFIG_CONTENTS)
        .when(decorator)
        .addOrUpdateConfigValueInConfigContents(
            VENDING_FILE_CONTENTS, STRING_TYPE, VENDING_CONFIG_NAME, VENDING_CONFIG_VALUE_DISABLE);
    when(androidFileUtil.push(eq(DEVICE_ID), eq(SDK_VERSION), anyString(), anyString()))
        .thenReturn("dummy log");
    when(localFileUtil.createTempFile(anyString(), anyString(), isNull()))
        .thenReturn(CONFIG_FILE_PATH);

    decorator.run(testInfo);

    verify(localFileUtil).writeToFile(CONFIG_FILE_PATH, FINKSY_CONFIG_CONTENTS);
    verify(localFileUtil).writeToFile(CONFIG_FILE_PATH, VENDING_CONFIG_CONTENTS);
    verify(androidFileUtil)
        .push(eq(DEVICE_ID), eq(SDK_VERSION), anyString(), eq(FINSKY_CONFIG_FILE));
    verify(androidFileUtil)
        .push(eq(DEVICE_ID), eq(SDK_VERSION), anyString(), eq(VENDING_CONFIG_FILE));
    verify(adbUtil)
        .broadcast(
            UtilArgs.builder().setSerial(DEVICE_ID).build(),
            IntentArgs.builder()
                .setAction(GSERVICES_OVERRIDE_ACTION)
                .setExtras(
                    ImmutableMap.of(
                        "finsky.setup_wizard_additional_account_vpa_enable",
                        Boolean.toString(false)))
                .build());
    verify(adbUtil)
        .broadcast(
            UtilArgs.builder().setSerial(DEVICE_ID).build(),
            IntentArgs.builder()
                .setAction(GSERVICES_OVERRIDE_ACTION)
                .setExtras(
                    ImmutableMap.of(
                        "finsky.play_services_auto_update_enabled", Boolean.toString(false)))
                .build());
  }

  @Test
  public void testReenableRun() throws MobileHarnessException, InterruptedException, IOException {
    AndroidDisableAutoUpdatesDecorator decorator =
        spy(
            new AndroidDisableAutoUpdatesDecorator(
                decoratedDriver,
                testInfo,
                localFileUtil,
                androidFileUtil,
                adb,
                adbUtil,
                systemSettingUtil));
    jobInfo.params().add(AndroidDisableAutoUpdatesDecorator.PARAM_ENABLE_UPDATES, "true");

    when(device.isRooted()).thenReturn(true);
    doReturn(FINSKY_FILE_CONTENTS)
        .when(decorator)
        .getDeviceFileContentsOrBlankDoc(DEVICE_ID, FINSKY_CONFIG_FILE);
    doReturn(VENDING_FILE_CONTENTS)
        .when(decorator)
        .getDeviceFileContentsOrBlankDoc(DEVICE_ID, VENDING_CONFIG_FILE);

    doReturn(FINKSY_CONFIG_CONTENTS)
        .when(decorator)
        .addOrUpdateConfigValueInConfigContents(
            FINSKY_FILE_CONTENTS, BOOL_TYPE, FINSKY_CONFIG_NAME, FINSKY_CONFIG_VALUE_ENABLE);
    doReturn(VENDING_CONFIG_CONTENTS)
        .when(decorator)
        .addOrUpdateConfigValueInConfigContents(
            VENDING_FILE_CONTENTS, STRING_TYPE, VENDING_CONFIG_NAME, VENDING_CONFIG_VALUE_ENABLE);
    when(androidFileUtil.push(eq(DEVICE_ID), eq(SDK_VERSION), anyString(), anyString()))
        .thenReturn("dummy log");
    when(localFileUtil.createTempFile(anyString(), anyString(), isNull()))
        .thenReturn(CONFIG_FILE_PATH);

    decorator.run(testInfo);

    verify(localFileUtil).writeToFile(CONFIG_FILE_PATH, FINKSY_CONFIG_CONTENTS);
    verify(localFileUtil).writeToFile(CONFIG_FILE_PATH, VENDING_CONFIG_CONTENTS);
    verify(androidFileUtil)
        .push(eq(DEVICE_ID), eq(SDK_VERSION), anyString(), eq(FINSKY_CONFIG_FILE));
    verify(androidFileUtil)
        .push(eq(DEVICE_ID), eq(SDK_VERSION), anyString(), eq(VENDING_CONFIG_FILE));
    verify(adbUtil)
        .broadcast(
            UtilArgs.builder().setSerial(DEVICE_ID).build(),
            IntentArgs.builder()
                .setAction(GSERVICES_OVERRIDE_ACTION)
                .setExtras(
                    ImmutableMap.of(
                        "finsky.setup_wizard_additional_account_vpa_enable",
                        Boolean.toString(true)))
                .build());
    verify(adbUtil)
        .broadcast(
            UtilArgs.builder().setSerial(DEVICE_ID).build(),
            IntentArgs.builder()
                .setAction(GSERVICES_OVERRIDE_ACTION)
                .setExtras(
                    ImmutableMap.of(
                        "finsky.play_services_auto_update_enabled", Boolean.toString(true)))
                .build());
  }

  @Test
  public void testRootExceptionThrown()
      throws MobileHarnessException, InterruptedException, IOException {
    AndroidDisableAutoUpdatesDecorator decorator =
        spy(
            new AndroidDisableAutoUpdatesDecorator(
                decoratedDriver,
                testInfo,
                localFileUtil,
                androidFileUtil,
                adb,
                adbUtil,
                systemSettingUtil));
    jobInfo.params().add(AndroidDisableAutoUpdatesDecorator.PARAM_ENABLE_UPDATES, "false");

    when(device.isRooted()).thenReturn(false);

    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));
    assertThat(e.getErrorId())
        .isEqualTo(
            AndroidErrorId
                .ANDROID_DISABLE_AUTO_UPDATES_DECORATOR_UNABLE_TO_UPDATE_CONFIG_FOR_UNROOTED_DEVICE);
    assertThat(e)
        .hasMessageThat()
        .contains("Please use AndroidDisableAutoUpdatesDecorator in a rooted device.");
  }

  @Test
  public void addOrUpdateConfigValueInConfigContents_malformedXml_throwsException() {
    AndroidDisableAutoUpdatesDecorator decorator =
        new AndroidDisableAutoUpdatesDecorator(
            decoratedDriver,
            testInfo,
            localFileUtil,
            androidFileUtil,
            adb,
            adbUtil,
            systemSettingUtil);

    assertThrows(
        MobileHarnessException.class,
        () ->
            decorator.addOrUpdateConfigValueInConfigContents(
                "<map><invalid", STRING_TYPE, NAME, VALUE));
  }

  @Test
  public void addOrUpdateConfigValueInConfigContents_multipleMapElements_throwsException() {
    AndroidDisableAutoUpdatesDecorator decorator =
        new AndroidDisableAutoUpdatesDecorator(
            decoratedDriver,
            testInfo,
            localFileUtil,
            androidFileUtil,
            adb,
            adbUtil,
            systemSettingUtil);
    String fileContents =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root><map name='1'></map><map"
            + " name='2'></map></root>";

    MobileHarnessException e =
        assertThrows(
            MobileHarnessException.class,
            () ->
                decorator.addOrUpdateConfigValueInConfigContents(
                    fileContents, STRING_TYPE, NAME, VALUE));
    assertThat(e.getCause()).isInstanceOf(IllegalStateException.class);
    assertThat(e.getCause())
        .hasMessageThat()
        .contains("Expected exactly 1 \"map\" element; found 2");
  }

  @Test
  public void addOrUpdateConfigValueInConfigContents_xxeInjection_throwsException() {
    AndroidDisableAutoUpdatesDecorator decorator =
        new AndroidDisableAutoUpdatesDecorator(
            decoratedDriver,
            testInfo,
            localFileUtil,
            androidFileUtil,
            adb,
            adbUtil,
            systemSettingUtil);
    String xxePayload =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!DOCTYPE map [\n"
            + "  <!ENTITY xxe SYSTEM \"file:///etc/passwd\">\n"
            + "]>\n"
            + "<map>&xxe;</map>";

    MobileHarnessException e =
        assertThrows(
            MobileHarnessException.class,
            () ->
                decorator.addOrUpdateConfigValueInConfigContents(
                    xxePayload, STRING_TYPE, NAME, VALUE));
    assertThat(e.getCause()).isInstanceOf(SAXParseException.class);
    assertThat(e.getCause()).hasMessageThat().contains("disallow-doctype-decl");
  }

  private String getStringTag(String name, String value) {
    return String.format("<%1$s name=\"%2$s\">%3$s</%1$s>", STRING_TYPE, name, value);
  }

  private String getNonStringTag(String type, String name, String value) {
    return String.format("<%s name=\"%s\" value=\"%s\"/>", type, name, value);
  }

  /** Returns the content wrapped in the XML header / map tag. */
  private String settingsContent(String content) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><map>" + content + "</map>";
  }
}
