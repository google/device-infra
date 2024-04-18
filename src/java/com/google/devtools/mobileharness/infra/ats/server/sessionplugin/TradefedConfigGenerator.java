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

package com.google.devtools.mobileharness.infra.ats.server.sessionplugin;

import com.google.common.base.Strings;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.DeviceActionConfigObject;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.DeviceActionConfigObject.Option;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestEnvironment;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

/** Generates Tradefed test configuration XML. */
public class TradefedConfigGenerator {
  private static final String ENCODING = "UTF-8";
  private static final String NULL_NS = null; // namespace used for XML serializer

  private static final String CMD_OPTIONS_TAG = "cmd_options";
  private static final String CONFIGURATION_TAG = "configuration";
  private static final String OPTION_TAG = "option";
  private static final String RESULT_REPORTER_TAG = "result_reporter";
  private static final String TARGET_PREPARER_TAG = "target_preparer";
  private static final String TEST_TAG = "test";

  private static final String CLASS_ATTR = "class";
  private static final String KEY_ATTR = "key";
  private static final String NAME_ATTR = "name";
  private static final String VALUE_ATTR = "value";

  private static final String TEST_CLASS = "com.android.tradefed.cluster.ClusterCommandLauncher";
  private static final String CMD_OPTIONS_CLASS = "com.android.tradefed.command.CommandOptions";

  static final Pattern BOOL_PATTERN = Pattern.compile("(?<prefix>no-)?(?<name>.+)");

  private TradefedConfigGenerator() {}

  public static void generateXml(OutputStream outputStream, TestEnvironment testEnvironment)
      throws XmlPullParserException, IOException {
    XmlSerializer serializer = XmlPullParserFactory.newInstance().newSerializer();
    serializer.setOutput(outputStream, ENCODING);
    serializer.setFeature(
        "http://xmlpull.org/v1/doc/features.html#indent-output", /* state= */ true);

    serializer.startDocument(ENCODING, /* standalone= */ false);
    serializer.startTag(NULL_NS, CONFIGURATION_TAG);

    for (DeviceActionConfigObject deviceActionConfigObject :
        testEnvironment.getDeviceActionConfigObjectsList()) {
      serializeDeviceAction(serializer, deviceActionConfigObject);
    }
    serializeTest(serializer, testEnvironment);
    serializeCmdOptions(serializer, testEnvironment);
    // TODO: redirect tool logs

    serializer.endTag(NULL_NS, CONFIGURATION_TAG);
    serializer.endDocument();
  }

  private static void serializeDeviceAction(
      XmlSerializer serializer, DeviceActionConfigObject configObject) throws IOException {
    String actionTag;
    switch (configObject.getType()) {
      case TARGET_PREPARER:
        actionTag = TARGET_PREPARER_TAG;
        break;
      case RESULT_REPORTER:
        actionTag = RESULT_REPORTER_TAG;
        break;
      default:
        // Skip for non-TF device action
        return;
    }
    serializer.startTag(NULL_NS, actionTag);
    serializer.attribute(NULL_NS, CLASS_ATTR, configObject.getClassName());
    for (Option option : configObject.getOptionValuesList()) {
      serializeOptionObject(serializer, option);
    }
    serializer.endTag(NULL_NS, actionTag);
  }

  private static void serializeTest(XmlSerializer serializer, TestEnvironment testEnvironment)
      throws IOException {
    serializer.startTag(NULL_NS, TEST_TAG);
    serializer.attribute(NULL_NS, CLASS_ATTR, TEST_CLASS);
    serializeOption(serializer, "root-dir", "${TF_WORK_DIR}");
    serializeOption(serializer, "command-line", "${COMMAND}");
    for (String key : testEnvironment.getEnvVarsMap().keySet()) {
      // Generate templates for env vars. Resolve them on the lab side.
      serializeOption(serializer, "env-var", key, String.format("${%s}", key));
    }
    for (String script : testEnvironment.getSetupScriptsList()) {
      serializeOption(serializer, "setup-script", script);
    }
    if (testEnvironment.getUseSubprocessReporting()) {
      serializeOption(serializer, "use-subprocess-reporting", "true");
    }
    long outputIdleTimeout =
        TimeUtils.toJavaDuration(testEnvironment.getOutputIdleTimeout()).toMillis();
    serializeOption(
        serializer,
        "output-idle-timeout",
        String.valueOf(outputIdleTimeout > 0 ? outputIdleTimeout : Long.MAX_VALUE));
    for (String jvmOption : testEnvironment.getJvmOptionsList()) {
      serializeOption(serializer, "jvm-option", jvmOption);
    }
    for (Entry<String, String> property : testEnvironment.getJavaPropertiesMap().entrySet()) {
      serializeOption(serializer, "java-property", property.getKey(), property.getValue());
    }
    serializer.endTag(NULL_NS, TEST_TAG);
  }

  private static void serializeCmdOptions(XmlSerializer serializer, TestEnvironment testEnvironment)
      throws IOException {
    serializer.startTag(NULL_NS, CMD_OPTIONS_TAG);
    serializer.attribute(NULL_NS, CLASS_ATTR, CMD_OPTIONS_CLASS);
    serializeOption(serializer, "test-tag", "cluster_command_launcher");
    if (testEnvironment.getUseParallelSetup()) {
      serializeOption(serializer, "parallel-setup", "true");
      serializeOption(serializer, "parallel-setup-timeout", "PT0S");
    }
    serializer.endTag(NULL_NS, CMD_OPTIONS_TAG);
  }

  private static void serializeOptionObject(XmlSerializer serializer, Option option)
      throws IOException {
    if (option.getValueList().isEmpty()) {
      Matcher m = BOOL_PATTERN.matcher(option.getName());
      if (m.find()) {
        String name = m.group("name");
        String value = "true";
        if (!Strings.isNullOrEmpty(m.group("prefix"))) {
          value = "false";
        }
        serializeOption(serializer, name, value);
      }
    } else {
      for (String value : option.getValueList()) {
        String[] parts = value.split("=", 2);
        if (parts.length == 2) {
          serializeOption(serializer, option.getName(), parts[0], parts[1]);
        } else {
          serializeOption(serializer, option.getName(), value);
        }
      }
    }
  }

  private static void serializeOption(XmlSerializer serializer, String name, String value)
      throws IOException {
    serializer.startTag(NULL_NS, OPTION_TAG);
    serializer.attribute(NULL_NS, NAME_ATTR, name);
    serializer.attribute(NULL_NS, VALUE_ATTR, value);
    serializer.endTag(NULL_NS, OPTION_TAG);
  }

  private static void serializeOption(
      XmlSerializer serializer, String name, String key, String value) throws IOException {
    serializer.startTag(NULL_NS, OPTION_TAG);
    serializer.attribute(NULL_NS, NAME_ATTR, name);
    serializer.attribute(NULL_NS, KEY_ATTR, key);
    serializer.attribute(NULL_NS, VALUE_ATTR, value);
    serializer.endTag(NULL_NS, OPTION_TAG);
  }
}
