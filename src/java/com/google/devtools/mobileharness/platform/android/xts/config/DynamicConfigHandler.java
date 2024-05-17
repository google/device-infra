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

package com.google.devtools.mobileharness.platform.android.xts.config;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

/**
 * A handler to process {@code DynamicConfig}. Branched from {@code
 * com.android.compatibility.common.util.DynamicConfigHandler} from Android codebase.
 */
public class DynamicConfigHandler {

  private static final String FILE_EXT = ".dynamic";
  private static final String NS = null; // xml constant representing null namespace
  private static final String ENCODING = "UTF-8";

  public static File getMergedDynamicConfigFile(
      File localConfigFile,
      String apbsConfigJson,
      String moduleName,
      Map<String, String> valueReplacementMap)
      throws XmlPullParserException, IOException {
    Map<String, List<String>> dynamicConfig = new HashMap<>();

    Map<String, List<String>> localConfig = DynamicConfig.createConfigMap(localConfigFile);
    Map<String, List<String>> apbsConfig = parseJsonToConfigMap(apbsConfigJson);
    localConfig.putAll(apbsConfig);

    localConfig.forEach((k, v) -> dynamicConfig.put(k, updateValues(v, valueReplacementMap)));

    setRemoteConfigRetrieved(dynamicConfig, apbsConfigJson != null);
    return storeMergedConfigFile(dynamicConfig, moduleName);
  }

  private static List<String> updateValues(
      List<String> values, Map<String, String> valueReplacementMap) {
    List<String> updatedValues = new ArrayList<>();
    values.forEach(
        v -> {
          String updatedValue = v;
          for (Map.Entry<String, String> entry : valueReplacementMap.entrySet()) {
            updatedValue = updatedValue.replace(entry.getKey(), entry.getValue());
          }
          updatedValues.add(updatedValue);
        });
    return updatedValues;
  }

  private static void setRemoteConfigRetrieved(
      Map<String, List<String>> config, boolean retrieved) {
    List<String> val = Collections.singletonList(Boolean.toString(retrieved));
    config.put(DynamicConfig.REMOTE_CONFIG_RETRIEVED_KEY, val);
  }

  private static Map<String, List<String>> parseJsonToConfigMap(String apbsConfigJson)
      throws JsonSyntaxException {

    Map<String, List<String>> configMap = new HashMap<String, List<String>>();
    if (apbsConfigJson == null) {
      return configMap;
    }

    JsonObject rootObj = new Gson().fromJson(apbsConfigJson, JsonObject.class);
    JsonObject configObject = rootObj.get("dynamicConfigEntries").getAsJsonObject();

    if (configObject == null) {
      // no config key-value(s) pairs have been defined remotely, return empty map
      return configMap;
    }

    for (String key : configObject.keySet()) {
      JsonArray jsonValues = configObject.getAsJsonObject(key).getAsJsonArray("configValues");
      configMap.put(
          key,
          jsonValues.asList().stream().map(JsonElement::getAsString).collect(toImmutableList()));
    }

    return configMap;
  }

  private static File storeMergedConfigFile(Map<String, List<String>> configMap, String moduleName)
      throws XmlPullParserException, IOException {
    File mergedConfigFile = File.createTempFile(moduleName, FILE_EXT, null);
    OutputStream stream = new FileOutputStream(mergedConfigFile);
    XmlSerializer serializer = XmlPullParserFactory.newInstance().newSerializer();
    serializer.setOutput(stream, ENCODING);
    serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
    serializer.startDocument(ENCODING, false);

    serializer.startTag(NS, DynamicConfig.CONFIG_TAG);
    for (String key : configMap.keySet()) {
      serializer.startTag(NS, DynamicConfig.ENTRY_TAG);
      serializer.attribute(NS, DynamicConfig.KEY_ATTR, key);
      for (String value : configMap.get(key)) {
        serializer.startTag(NS, DynamicConfig.VALUE_TAG);
        serializer.text(value);
        serializer.endTag(NS, DynamicConfig.VALUE_TAG);
      }
      serializer.endTag(NS, DynamicConfig.ENTRY_TAG);
    }
    serializer.endTag(NS, DynamicConfig.CONFIG_TAG);
    serializer.endDocument();
    return mergedConfigFile;
  }

  private DynamicConfigHandler() {}
}
