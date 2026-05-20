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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
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
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.StringReader;
import java.io.StringWriter;
import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Driver decorator for disabling auto-updates in the Play Store */
@DecoratorAnnotation(help = "For disabling auto-updates in the Play Store. ")
public class AndroidDisableAutoUpdatesDecorator extends BaseDecorator {

  @ParamAnnotation(
      required = false,
      help =
          "A boolean flag that allows a user to re-enable updates prior to their test run."
              + "The default is 'false'.")
  public static final String PARAM_ENABLE_UPDATES = "enable_updates";

  // In testing, it looks like the finsky file is what actually is looked at to control auto-update,
  // but the vending file is kept in sync with this file. So, we change both to make sure that there
  // isn't any unexpected behavior if some code is looking at the vending file.
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
  private static final String BLANK_CONFIG =
      "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?><map></map>";
  private static final String GSERVICES_OVERRIDE_ACTION =
      "com.google.gservices.intent.action.GSERVICES_OVERRIDE";
  private static final String BOOL_TYPE = "boolean";
  private static final String STRING_TYPE = "string";
  private static final String MAP_TAG = "map";
  private static final String NAME_ATTR = "name";
  private static final String VALUE_ATTR = "value";

  private final LocalFileUtil localFileUtil;
  private final AndroidFileUtil androidFileUtil;
  private final Adb adb;
  private final AndroidAdbUtil adbUtil;
  private final AndroidSystemSettingUtil systemSettingUtil;

  public AndroidDisableAutoUpdatesDecorator(Driver decoratedDriver, TestInfo testInfo) {
    this(
        decoratedDriver,
        testInfo,
        new LocalFileUtil(),
        new AndroidFileUtil(),
        new Adb(),
        new AndroidAdbUtil(),
        new AndroidSystemSettingUtil());
  }

  /** Constructor for testing only. */
  @VisibleForTesting
  AndroidDisableAutoUpdatesDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      LocalFileUtil localFileUtil,
      AndroidFileUtil androidFileUtil,
      Adb adb,
      AndroidAdbUtil adbUtil,
      AndroidSystemSettingUtil systemSettingUtil) {
    super(decoratedDriver, testInfo);
    this.localFileUtil = localFileUtil;
    this.androidFileUtil = androidFileUtil;
    this.adb = adb;
    this.adbUtil = adbUtil;
    this.systemSettingUtil = systemSettingUtil;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    JobInfo jobInfo = testInfo.jobInfo();
    boolean enableUpdates =
        jobInfo.params().getBool(PARAM_ENABLE_UPDATES, /* defaultValue= */ false);

    if (!((AndroidDevice) getDevice()).isRooted()) {
      throw new MobileHarnessException(
          AndroidErrorId
              .ANDROID_DISABLE_AUTO_UPDATES_DECORATOR_UNABLE_TO_UPDATE_CONFIG_FOR_UNROOTED_DEVICE,
          "Please use AndroidDisableAutoUpdatesDecorator in a rooted device.");
    }

    try {
      String serial = getDevice().getDeviceId();
      if (enableUpdates) {
        configurePlayStoreUpdates(
            serial, testInfo, FINSKY_CONFIG_VALUE_ENABLE, VENDING_CONFIG_VALUE_ENABLE);
        configureForcedUpdates(serial, true);
      } else {
        configurePlayStoreUpdates(
            serial, testInfo, FINSKY_CONFIG_VALUE_DISABLE, VENDING_CONFIG_VALUE_DISABLE);
        configureForcedUpdates(serial, false);
      }
    } catch (MobileHarnessException e) {
      MobileHarnessException mhEx =
          new MobileHarnessException(
              AndroidErrorId
                  .ANDROID_DISABLE_AUTO_UPDATES_DECORATOR_FAILED_TO_UPDATE_AUTO_UPDATE_CONFIG,
              "Exception during configuring update: " + e.getMessage(),
              e);
      testInfo.warnings().addAndLog(mhEx);
      throw mhEx;
    }
    getDecorated().run(testInfo);
  }

  /** Disables auto-update in both Play Store config files. */
  private void configurePlayStoreUpdates(
      String serial, TestInfo testInfo, String finksyConfigValue, String vendingConfigValue)
      throws InterruptedException, MobileHarnessException {
    pushFile(
        serial,
        addOrUpdateConfigValueInConfigContents(
            getDeviceFileContentsOrBlankDoc(serial, FINSKY_CONFIG_FILE),
            BOOL_TYPE,
            FINSKY_CONFIG_NAME,
            finksyConfigValue),
        FINSKY_CONFIG_FILE,
        testInfo);
    pushFile(
        serial,
        addOrUpdateConfigValueInConfigContents(
            getDeviceFileContentsOrBlankDoc(serial, VENDING_CONFIG_FILE),
            STRING_TYPE,
            VENDING_CONFIG_NAME,
            vendingConfigValue),
        VENDING_CONFIG_FILE,
        testInfo);
  }

  /* Gets the contents of a file on the device. */
  String getDeviceFileContentsOrBlankDoc(String serial, String file)
      throws InterruptedException, MobileHarnessException {
    if (!androidFileUtil.isFileOrDirExisted(serial, file)) {
      return BLANK_CONFIG;
    }
    String contents = adb.runShell(serial, "cat " + file);
    // If the file doesn't exist, it will be Null
    return Strings.isNullOrEmpty(contents) ? BLANK_CONFIG : contents;
  }

  /** Updates the setting in the config file contents if present, or adds it if not present. */
  @VisibleForTesting
  String addOrUpdateConfigValueInConfigContents(
      String existingContents, String valueType, String name, String value)
      throws MobileHarnessException {
    try {
      Document configDoc = parseXmlSecurely(existingContents);
      Element mapNode = checkSingleMapElement(configDoc);
      Element configElem = findConfigElement(mapNode, valueType, name);

      if (configElem == null) {
        configElem = checkIsElement(mapNode.appendChild(configDoc.createElement(valueType)));
        configElem.setAttribute(NAME_ATTR, name);
      }

      if (valueType.equals(STRING_TYPE)) {
        // Currently, it looks like string tags are the only tags to store data in text content
        // rather than in a "value" attribute.
        configElem.setTextContent(value);
      } else {
        configElem.setAttribute(VALUE_ATTR, value);
      }

      return serializeXmlToString(configDoc);
    } catch (Exception e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DISABLE_AUTO_UPDATES_DECORATOR_FAILED_TO_WRITE_CONFIG_FILE,
          e.getMessage(),
          e);
    }
  }

  private static Document parseXmlSecurely(String existingContents) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    // Disable external DTDs for security
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    DocumentBuilder builder = factory.newDocumentBuilder();
    return builder.parse(new InputSource(new StringReader(existingContents)));
  }

  private static String serializeXmlToString(Document doc) throws Exception {
    Transformer transformer = TransformerFactory.newInstance().newTransformer();
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    StringWriter writer = new StringWriter();
    transformer.transform(new DOMSource(doc), new StreamResult(writer));
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + writer;
  }

  /** Assert that a single {@link Element} named "map" is present in the document. */
  private Element checkSingleMapElement(Document doc) {
    NodeList list = doc.getElementsByTagName(MAP_TAG);
    int length = list.getLength();
    checkState(length == 1, String.format("Expected exactly 1 \"map\" element; found %d", length));
    return checkIsElement(list.item(0));
  }

  /** Assert that a {@link Node} is actually an {@link Element}. */
  private Element checkIsElement(Node node) {
    checkState(node instanceof Element, "%s node was not an Element", node.getNodeName());
    return (Element) node;
  }

  /** Returns the first {@link Element} with the given {@code tag} and {@code name} attribute. */
  @Nullable
  private Element findConfigElement(Element mapNode, String tag, String name) {
    NodeList typeNodes = mapNode.getElementsByTagName(tag);
    for (int i = 0; i < typeNodes.getLength(); i++) {
      Element child = checkIsElement(typeNodes.item(i));
      if (name.equals(child.getAttribute(NAME_ATTR))) {
        return child;
      }
    }
    return null;
  }

  /** Copies the content to the destination on the device. */
  private void pushFile(String serial, String content, String destinationPath, TestInfo testInfo)
      throws InterruptedException, MobileHarnessException {
    // Can't directly write files to the device; need to write locally, then use ADB to copy it.
    String tempConfigFile = localFileUtil.createTempFile(testInfo.getTmpFileDir(), "config", null);
    localFileUtil.writeToFile(tempConfigFile, content);
    int sdkVersion = systemSettingUtil.getDeviceSdkVersion(serial);
    androidFileUtil.push(serial, sdkVersion, tempConfigFile, destinationPath);
  }

  /** Disable or enables auto-update mechanisms which are usually not user-controllable. */
  private void configureForcedUpdates(String serial, boolean enableUpdates)
      throws InterruptedException, MobileHarnessException {
    // Prevents triggering Virtual Preloaded App processing when an account is added.
    adbUtil.broadcast(
        UtilArgs.builder().setSerial(serial).build(),
        IntentArgs.builder()
            .setAction(GSERVICES_OVERRIDE_ACTION)
            .setExtras(
                ImmutableMap.of(
                    "finsky.setup_wizard_additional_account_vpa_enable",
                    Boolean.toString(enableUpdates)))
            .build());

    // Prevents GmsCore from doing auto-updates.
    adbUtil.broadcast(
        UtilArgs.builder().setSerial(serial).build(),
        IntentArgs.builder()
            .setAction(GSERVICES_OVERRIDE_ACTION)
            .setExtras(
                ImmutableMap.of(
                    "finsky.play_services_auto_update_enabled", Boolean.toString(enableUpdates)))
            .build());
  }
}
