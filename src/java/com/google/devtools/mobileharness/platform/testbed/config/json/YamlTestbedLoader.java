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

package com.google.devtools.mobileharness.platform.testbed.config.json;

import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.testbed.config.TestbedConfig;
import com.google.devtools.mobileharness.platform.testbed.config.TestbedLoader;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.parser.ParserException;

/** Helper class for loading in a testbed configuration. */
public class YamlTestbedLoader implements TestbedLoader {

  private final String yamlString;

  public static YamlTestbedLoader fromString(String yamlString) {
    return new YamlTestbedLoader(yamlString);
  }

  public static YamlTestbedLoader fromFilename(String filename) throws MobileHarnessException {
    LocalFileUtil fileUtil = new LocalFileUtil();
    String text = fileUtil.readFile(filename);
    return new YamlTestbedLoader(text);
  }

  @Override
  public Map<String, TestbedConfig> getTestbedConfigs() throws MobileHarnessException {
    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
    Gson gson = new Gson();

    Map<String, TestbedConfig> container = new HashMap<>();
    try {
      JSONArray json =
          new JSONArray(
              gson.toJson(yaml.load(yamlString), new TypeToken<Collection<Object>>() {}.getType()));
      for (int i = 0; i < json.length(); i++) {
        JSONObject testbedJson = json.getJSONObject(i);
        TestbedConfig testbed = JsonTestbedConfig.create(testbedJson);
        if (container.containsKey(testbed.getName())) {
          throw new MobileHarnessException(
              ExtErrorId.TESTBED_CONFIG_DUPLICATE_ID_ERROR,
              String.format(
                  "Testbed yaml file contains duplicate testbed names (%s). Testbed names must be"
                      + " unique within a file.",
                  testbed.getName()));
        }
        container.put(testbed.getName(), testbed);
      }
    } catch (ClassCastException | JSONException | ParserException e) {
      throw new MobileHarnessException(
          ExtErrorId.TESTBED_YAML_PARSING_ERROR,
          String.format("Invalid YAML format.\n%s", yamlString),
          e);
    }
    return container;
  }

  private YamlTestbedLoader(String yamlString) {
    this.yamlString = yamlString;
  }
}
