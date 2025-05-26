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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.DeviceActionConfigObject;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.Option;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestEnvironment;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestResource;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Printer;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
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

  private static final String BUILD_PROVIDER_TAG = "build_provider";
  private static final String CMD_OPTIONS_TAG = "cmd_options";
  private static final String CONFIGURATION_TAG = "configuration";
  private static final String DEVICE_TAG = "device";
  private static final String LOG_SAVER_TAG = "log_saver";
  private static final String OPTION_TAG = "option";
  private static final String RESULT_REPORTER_TAG = "result_reporter";
  private static final String TARGET_PREPARER_TAG = "target_preparer";
  private static final String TEST_TAG = "test";

  private static final String CLASS_ATTR = "class";
  private static final String KEY_ATTR = "key";
  private static final String NAME_ATTR = "name";
  private static final String VALUE_ATTR = "value";

  private static final String BUILD_PROVIDER_CLASS =
      "com.android.tradefed.cluster.ClusterBuildProvider";
  private static final String CLUSTER_LOG_SAVER_CLASS =
      "com.android.tradefed.cluster.ClusterLogSaver";
  private static final String CMD_OPTIONS_CLASS = "com.android.tradefed.command.CommandOptions";
  private static final String TEST_CLASS = "com.android.tradefed.cluster.ClusterCommandLauncher";

  private static final Pattern BOOL_PATTERN = Pattern.compile("(?<prefix>no-)?(?<name>.+)");
  private static final Pattern LIMIT_RATE_PATTERN = Pattern.compile("^\\d+[KMG]$");

  private static final Printer JSON_PRINTER =
      JsonFormat.printer().omittingInsignificantWhitespace().preservingProtoFieldNames();

  public static final String COMMAND_LINE_TEMPLATE = "${COMMAND}";
  public static final String FILE_TEMPLATE = "${FILE_%s}";
  public static final String OUTPUT_DIR_TEMPLATE = "${OUTPUT_DIR}";

  private TradefedConfigGenerator() {}

  public static void generateXml(
      OutputStream outputStream,
      TestEnvironment testEnvironment,
      List<TestResource> testResources,
      int deviceCount)
      throws XmlPullParserException, IOException {
    XmlSerializer serializer = XmlPullParserFactory.newInstance().newSerializer();
    serializer.setOutput(outputStream, ENCODING);
    serializer.setFeature(
        "http://xmlpull.org/v1/doc/features.html#indent-output", /* state= */ true);

    serializer.startDocument(ENCODING, /* standalone= */ false);
    serializer.startTag(NULL_NS, CONFIGURATION_TAG);
    if (deviceCount > 1) {
      ImmutableSet<DeviceActionConfigObject> deviceActionResultReporters =
          testEnvironment.getDeviceActionConfigObjectsList().stream()
              .filter(
                  deviceActionConfigObject ->
                      deviceActionConfigObject.getType()
                          == DeviceActionConfigObject.DeviceActionConfigObjectType.RESULT_REPORTER)
              .collect(toImmutableSet());
      ImmutableList<DeviceActionConfigObject> remainingDeviceActionConfigObjects =
          testEnvironment.getDeviceActionConfigObjectsList().stream()
              .filter(
                  deviceActionConfigObject ->
                      deviceActionConfigObject.getType()
                          != DeviceActionConfigObject.DeviceActionConfigObjectType.RESULT_REPORTER)
              .collect(toImmutableList());
      for (int i = 0; i < deviceCount; i++) {
        serializer.startTag(NULL_NS, DEVICE_TAG);
        serializer.attribute(NULL_NS, NAME_ATTR, String.format("TF_DEVICE_%d", i));
        serializedDevicePreparers(serializer, remainingDeviceActionConfigObjects, testResources);
        serializer.endTag(NULL_NS, DEVICE_TAG);
      }
      for (DeviceActionConfigObject deviceActionResultReporter : deviceActionResultReporters) {
        serializeDeviceAction(serializer, deviceActionResultReporter);
      }
    } else {
      serializedDevicePreparers(
          serializer, testEnvironment.getDeviceActionConfigObjectsList(), testResources);
    }
    serializeTest(serializer, testEnvironment);
    serializeCmdOptions(serializer, testEnvironment);
    serializeLogSaver(serializer);
    serializeTradefedOptions(serializer, testEnvironment);
    serializer.endTag(NULL_NS, CONFIGURATION_TAG);
    serializer.endDocument();
  }

  private static void serializeTradefedOptions(
      XmlSerializer serializer, TestEnvironment testEnvironment) throws IOException {
    for (Option option : testEnvironment.getTradefedOptionsList()) {
      serializeOptionObject(serializer, option);
    }
  }

  private static void serializedDevicePreparers(
      XmlSerializer serializer,
      List<DeviceActionConfigObject> deviceActionConfigObjects,
      List<TestResource> testResources)
      throws IOException {
    for (DeviceActionConfigObject deviceActionConfigObject : deviceActionConfigObjects) {
      serializeDeviceAction(serializer, deviceActionConfigObject);
    }
    if (!testResources.isEmpty()) {
      serializeBuildProvider(serializer, testResources);
    }
  }

  private static void serializeTest(XmlSerializer serializer, TestEnvironment testEnvironment)
      throws IOException {
    serializer.startTag(NULL_NS, TEST_TAG);
    serializer.attribute(NULL_NS, CLASS_ATTR, TEST_CLASS);
    serializeOption(serializer, "root-dir", "${TF_WORK_DIR}");
    serializeOption(serializer, "command-line", COMMAND_LINE_TEMPLATE);
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

  private static void serializeLogSaver(XmlSerializer serializer) throws IOException {
    serializer.startTag(NULL_NS, LOG_SAVER_TAG);
    serializer.attribute(NULL_NS, CLASS_ATTR, CLUSTER_LOG_SAVER_CLASS);
    serializeOption(serializer, "root-dir", "${TF_WORK_DIR}");
    serializeOption(serializer, "output-file-upload-url", OUTPUT_DIR_TEMPLATE);
    // Unused but required options
    serializeOption(serializer, "request-id", "");
    serializeOption(serializer, "command-id", "");
    serializeOption(serializer, "attempt-id", "");
    serializer.endTag(NULL_NS, LOG_SAVER_TAG);
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

  private static void serializeBuildProvider(
      XmlSerializer serializer, List<TestResource> testResources) throws IOException {
    serializer.startTag(NULL_NS, BUILD_PROVIDER_TAG);
    serializer.attribute(NULL_NS, CLASS_ATTR, BUILD_PROVIDER_CLASS);
    serializeOption(serializer, "root-dir", "${TF_WORK_DIR}");
    for (TestResource testResource : testResources) {
      serializeOption(serializer, "test-resource", serializeTestResource(testResource));
    }
    if (Flags.instance().tradefedCurlDownloadLimitRate.get() != null) {
      String limitRate = Flags.instance().tradefedCurlDownloadLimitRate.get();
      checkArgument(isLimitRateValue(limitRate), "Invalid curl download rate limit: %s", limitRate);
      serializeOption(serializer, "curl-limit-rate", limitRate);
    }
    serializer.endTag(NULL_NS, BUILD_PROVIDER_TAG);
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

  private static String serializeTestResource(TestResource testResource)
      throws InvalidProtocolBufferException {
    return JSON_PRINTER.print(
        testResource.toBuilder()
            // Replace the url with the template. Resolve it on the lab side.
            .setUrl(String.format(FILE_TEMPLATE, testResource.getName()))
            .build());
  }

  @VisibleForTesting
  static boolean isLimitRateValue(String value) {
    return LIMIT_RATE_PATTERN.matcher(value).matches();
  }
}
