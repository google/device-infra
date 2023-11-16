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

package com.google.devtools.mobileharness.infra.ats.console.testbed.config;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import javax.annotation.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Util class to update Mobly yaml testbed config. */
public class YamlTestbedUpdater {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String CONFIG_KEY_TESTBEDS = "TestBeds";
  private static final String CONFIG_KEY_TESTBEDS_NAME = "Name";
  private static final String CONFIG_KEY_TESTBED_CONTROLLERS = "Controllers";
  private static final String CONFIG_KEY_TESTBED_CONTROLLERS_ANDROID_DEVICE = "AndroidDevice";
  private static final String CONFIG_KEY_TESTBED_CONTROLLERS_ANDROID_DEVICE_SERIAL = "serial";

  private final Yaml yaml;
  private final Gson gson;
  private final LocalFileUtil localFileUtil;

  public YamlTestbedUpdater() {
    this(new Yaml(new SafeConstructor(new LoaderOptions())), new Gson(), new LocalFileUtil());
  }

  YamlTestbedUpdater(Yaml yaml, Gson gson, LocalFileUtil localFileUtil) {
    this.yaml = yaml;
    this.gson = gson;
    this.localFileUtil = localFileUtil;
  }

  /**
   * Inserts or updates Android device "serial" with given {@code serial} for the first found
   * TestBed in the given testbed yaml file, and generates a new testbed yaml file whose name
   * prepended with "updated_".
   *
   * @param testbedYaml the path to the testbed yaml file
   * @param serial device serial number being upserted
   * @param outputDir the directory in which the new generated testbed yaml file will be saved, if
   *     it's generated. If this param is not provided, the new testbed yaml file will be saved in
   *     the same directory as the original one.
   * @return the original testbed yaml config file if it's not upserted, otherwise the new testbed
   *     yaml config file with the given {@code serial} upserted.
   */
  public String upsertDeviceSerial(String testbedYaml, String serial, @Nullable String outputDir)
      throws MobileHarnessException {
    Path parent = outputDir == null ? Paths.get(testbedYaml).getParent() : Paths.get(outputDir);
    Path updatedTestbedYaml = parent.resolve("updated_" + Paths.get(testbedYaml).getFileName());

    JsonElement configJsonTree =
        gson.toJsonTree(
            yaml.load(localFileUtil.readFile(testbedYaml)),
            new TypeToken<Map<String, Object>>() {}.getType());
    if (configJsonTree.isJsonNull() || !configJsonTree.isJsonObject()) {
      logger.atWarning().log("Given testbed yaml file [%s] is not in valid format.", testbedYaml);
      return testbedYaml;
    }

    JsonObject configJson = configJsonTree.getAsJsonObject();
    JsonArray testBedsJsonArray = configJson.getAsJsonArray(CONFIG_KEY_TESTBEDS);

    boolean serialUpserted = false;
    for (JsonElement testBedJsonEl : testBedsJsonArray) {
      if (!testBedJsonEl.isJsonObject()) {
        continue;
      }
      JsonObject testBedJsonObj = testBedJsonEl.getAsJsonObject();
      if (testBedJsonObj.get(CONFIG_KEY_TESTBED_CONTROLLERS) == null
          || !testBedJsonObj.get(CONFIG_KEY_TESTBED_CONTROLLERS).isJsonObject()) {
        continue;
      }

      JsonObject controllers = testBedJsonObj.getAsJsonObject(CONFIG_KEY_TESTBED_CONTROLLERS);
      JsonElement androidDeviceJsonEl =
          controllers.get(CONFIG_KEY_TESTBED_CONTROLLERS_ANDROID_DEVICE);
      if (androidDeviceJsonEl == null) {
        continue;
      }

      if (androidDeviceJsonEl.isJsonPrimitive()) {
        JsonObject androidDeviceJsonObj = new JsonObject();
        androidDeviceJsonObj.addProperty(
            CONFIG_KEY_TESTBED_CONTROLLERS_ANDROID_DEVICE_SERIAL, serial);
        JsonArray androidDeviceJsonArray = new JsonArray();
        androidDeviceJsonArray.add(androidDeviceJsonObj);
        controllers.add(CONFIG_KEY_TESTBED_CONTROLLERS_ANDROID_DEVICE, androidDeviceJsonArray);
        serialUpserted = true;
      } else if (androidDeviceJsonEl.isJsonArray()) {
        for (JsonElement element : androidDeviceJsonEl.getAsJsonArray()) {
          JsonObject androidDeviceJsonObj = element.getAsJsonObject();
          androidDeviceJsonObj.addProperty(
              CONFIG_KEY_TESTBED_CONTROLLERS_ANDROID_DEVICE_SERIAL, serial);
          serialUpserted = true;
          break;
        }
      }

      if (serialUpserted) {
        break;
      }
    }

    if (!serialUpserted) {
      logger.atInfo().log("Found no AndroidDevice TestBed Controllers, uses original config.");
      return testbedYaml;
    }

    StringWriter configWriter = new StringWriter();
    Object configObject = yaml.load(configJson.toString());
    yaml.dump(configObject, configWriter);
    logger.atInfo().log("Writing updated config to [%s]", updatedTestbedYaml);
    localFileUtil.writeToFile(updatedTestbedYaml.toString(), configWriter.toString());
    return updatedTestbedYaml.toString();
  }

  /**
   * Prepares a Mobly testbed config file(.yaml) based on the given Android device serial numbers.
   *
   * @param androidDeviceSerials a list of serial numbers for the Android devices under tests
   * @param outputDir the directory in which the new generated testbed yaml file will be saved
   * @param outputConfigFileName name for the generated testbed config file. It uses testbed name as
   *     the file name if it's not given.
   * @return the path to the generated testbed yaml file
   */
  public String prepareMoblyConfig(
      ImmutableList<String> androidDeviceSerials,
      String outputDir,
      @Nullable String outputConfigFileName)
      throws MobileHarnessException {
    JsonArray androidDeviceJsonArray = new JsonArray();
    for (String serial : androidDeviceSerials) {
      androidDeviceJsonArray.add(createAndroidSubDevice(serial));
    }

    JsonObject controllersJsonObj = new JsonObject();
    controllersJsonObj.add(CONFIG_KEY_TESTBED_CONTROLLERS_ANDROID_DEVICE, androidDeviceJsonArray);
    JsonObject testBedJsonObj = new JsonObject();
    String testBedName = "testbed_" + Joiner.on("_").join(androidDeviceSerials);
    if (testBedName.contains(":")) {
      // Testbed name cannot have ':'
      testBedName = testBedName.replace(':', '-');
    }
    testBedJsonObj.addProperty(CONFIG_KEY_TESTBEDS_NAME, testBedName);
    testBedJsonObj.add(CONFIG_KEY_TESTBED_CONTROLLERS, controllersJsonObj);

    JsonArray testBedsJsonArray = new JsonArray();
    testBedsJsonArray.add(testBedJsonObj);

    JsonObject configJsonObj = new JsonObject();
    configJsonObj.add(CONFIG_KEY_TESTBEDS, testBedsJsonArray);

    if (outputConfigFileName == null) {
      outputConfigFileName = testBedName + ".yaml";
    }

    StringWriter configWriter = new StringWriter();
    Object configObject = yaml.load(configJsonObj.toString());
    yaml.dump(configObject, configWriter);
    Path config = Paths.get(outputDir, outputConfigFileName);
    logger.atInfo().log("Writing generated Mobly config to [%s]", config);
    localFileUtil.writeToFile(config.toString(), configWriter.toString());
    return config.toString();
  }

  private JsonObject createAndroidSubDevice(String serial) {
    JsonObject androidSubDeviceJsonObj = new JsonObject();
    androidSubDeviceJsonObj.addProperty(
        CONFIG_KEY_TESTBED_CONTROLLERS_ANDROID_DEVICE_SERIAL, serial);
    return androidSubDeviceJsonObj;
  }
}
