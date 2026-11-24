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

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
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
  private static final Gson GSON = new Gson();

  public static File getMergedDynamicConfigFile(
      File localConfigFile,
      @Nullable String apbsConfigJson,
      String moduleName,
      Map<String, String> valueReplacementMap)
      throws XmlPullParserException, IOException {
    return getMergedDynamicConfigFile(
        localConfigFile,
        apbsConfigJson == null ? null : parseJsonToConfigMap(apbsConfigJson),
        moduleName,
        valueReplacementMap);
  }

  public static File getMergedDynamicConfigFile(
      File localConfigFile,
      @Nullable Map<String, ? extends List<String>> apbsConfig,
      String moduleName,
      Map<String, String> valueReplacementMap)
      throws XmlPullParserException, IOException {
    Map<String, List<String>> dynamicConfig = new HashMap<>();

    Map<String, List<String>> localConfig = DynamicConfig.createConfigMap(localConfigFile);
    if (apbsConfig != null) {
      localConfig.putAll(apbsConfig);
    }

    localConfig.forEach((k, v) -> dynamicConfig.put(k, updateValues(v, valueReplacementMap)));

    setRemoteConfigRetrieved(dynamicConfig, apbsConfig != null);
    return storeMergedConfigFile(dynamicConfig, moduleName);
  }

  private static List<String> updateValues(
      List<String> values, Map<String, String> valueReplacementMap) {
    List<String> updatedValues = new ArrayList<>();
    values.forEach(v -> {
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

  /**
   * Parses partner dynamic config JSON into a map of config keys to value lists.
   *
   * <p>Returns an empty map if {@code apbsConfigJson} is {@code null} or lacks {@code
   * dynamicConfigEntries}, and maps entries without {@code configValues} to an empty list.
   */
  public static Map<String, List<String>> parseJsonToConfigMap(String apbsConfigJson)
      throws JsonSyntaxException {

    Map<String, List<String>> configMap = new HashMap<String, List<String>>();
    if (apbsConfigJson == null) {
      return configMap;
    }

    DynamicConfigResponse response = GSON.fromJson(apbsConfigJson, DynamicConfigResponse.class);
    if (response == null) {
      throw new JsonSyntaxException("Expected a JSON object for dynamic config");
    }
    if (response.entries() == null) {
      return configMap;
    }

    for (Map.Entry<String, DynamicConfigEntry> entry : response.entries().entrySet()) {
      DynamicConfigEntry value = entry.getValue();
      if (value == null || (value.configValues() != null && value.configValues().contains(null))) {
        throw new JsonSyntaxException(
            String.format(
                "Invalid dynamic config entry for key %s: %s",
                entry.getKey(), value == null ? null : value.configValues()));
      }
      configMap.put(
          entry.getKey(),
          value.configValues() == null
              ? ImmutableList.of()
              : ImmutableList.copyOf(value.configValues()));
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

  private record DynamicConfigResponse(
      @SerializedName("dynamicConfigEntries") Map<String, DynamicConfigEntry> entries) {}

  private record DynamicConfigEntry(@SerializedName("configValues") List<String> configValues) {}
}
