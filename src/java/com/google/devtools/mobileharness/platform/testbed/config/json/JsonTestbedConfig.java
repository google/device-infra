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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.testbed.config.BaseTestbedConfig;
import com.google.devtools.mobileharness.platform.testbed.config.SubDeviceInfo;
import com.google.devtools.mobileharness.platform.testbed.config.SubDeviceKey;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Implementation of {@link com.google.devtools.mobileharness.platform.testbed.config.TestbedConfig}
 * that handles loading testbed configurations from a {@link JSONObject}.
 */
public class JsonTestbedConfig extends BaseTestbedConfig {

  private static final String TESTBED_NAME_KEY = "name";
  private static final String DEVICES_KEY = "devices";
  private static final String PROPERTIES_KEY = "properties";
  private static final String DIMENSIONS_KEY = "dimensions";
  private static final String REQUIRED_DIMENSIONS_KEY = "required_dimensions";

  private JsonTestbedConfig(
      String name,
      Map<SubDeviceKey, SubDeviceInfo> devices,
      ImmutableListMultimap<String, String> dimensions,
      ImmutableListMultimap<String, String> requiredDimensions,
      Map<String, Object> properties)
      throws MobileHarnessException {
    super(name, devices, dimensions, requiredDimensions, properties);
  }

  /**
   * Creates a testbed config from a json object.
   *
   * @param jsonObject The json object to create the config from.
   * @throws MobileHarnessException Thrown if the testbed config could not be loaded.
   */
  public static JsonTestbedConfig create(JSONObject jsonObject)
      throws MobileHarnessException, JSONException {
    String name;
    if (jsonObject.has(TESTBED_NAME_KEY)) {
      name = jsonObject.getString(TESTBED_NAME_KEY);
    } else {
      throw new MobileHarnessException(
          ExtErrorId.TESTBED_PARSING_NAME_NOT_EXIST_ERROR,
          "Testbed definition does not have a name.\n" + jsonObject);
    }

    ImmutableMap.Builder<SubDeviceKey, SubDeviceInfo> devices = new ImmutableMap.Builder<>();
    if (jsonObject.has(DEVICES_KEY)) {
      JSONArray jsonDevices = jsonObject.getJSONArray(DEVICES_KEY);
      if (jsonDevices.length() == 0) {
        throw new MobileHarnessException(
            ExtErrorId.TESTBED_PARSING_DEVICE_NOT_EXIST_ERROR,
            "Must have at least one device in the testbed.\n" + jsonObject);
      }

      for (int i = 0; i < jsonDevices.length(); i++) {
        SubDeviceInfo device = JsonSubDeviceInfo.create(jsonDevices.getJSONObject(i), name, i);
        SubDeviceKey identifier = SubDeviceKey.create(device.getId(), device.getDeviceType());
        devices.put(identifier, device);
      }
    } else {
      throw new MobileHarnessException(
          ExtErrorId.TESTBED_PARSING_DEVICE_KEY_NOT_EXIST_ERROR,
          "Devices must be defined for the testbed.\n" + jsonObject);
    }

    ImmutableMap.Builder<String, Object> properties = new ImmutableMap.Builder<>();
    if (jsonObject.has(PROPERTIES_KEY)) {
      JSONObject jsonProperties = jsonObject.getJSONObject(PROPERTIES_KEY);
      Iterator<?> keyIt = jsonProperties.keys();
      while (keyIt.hasNext()) {
        String key = keyIt.next().toString();
        properties.put(key, jsonProperties.get(key));
      }
    }

    ImmutableListMultimap.Builder<String, String> dimensions =
        new ImmutableListMultimap.Builder<>();
    if (jsonObject.has(DIMENSIONS_KEY)) {
      JSONObject jsonDimensions = jsonObject.getJSONObject(DIMENSIONS_KEY);
      Iterator<?> keyIt = jsonDimensions.keys();
      while (keyIt.hasNext()) {
        String key = keyIt.next().toString();
        Object valueObj = jsonDimensions.get(key);
        if (valueObj instanceof JSONArray valueArray) {
          for (Object oneValue : valueArray) {
            dimensions.put(key, oneValue.toString());
          }
        } else {
          dimensions.put(key, valueObj.toString());
        }
      }
    }

    ImmutableListMultimap.Builder<String, String> requiredDimensions =
        new ImmutableListMultimap.Builder<>();
    if (jsonObject.has(REQUIRED_DIMENSIONS_KEY)) {
      JSONObject jsonRequiredDimensions = jsonObject.getJSONObject(REQUIRED_DIMENSIONS_KEY);
      Iterator<?> keyIt = jsonRequiredDimensions.keys();
      while (keyIt.hasNext()) {
        String key = keyIt.next().toString();
        Object valueObj = jsonRequiredDimensions.get(key);
        if (valueObj instanceof JSONArray valueArray) {
          for (Object oneValue : valueArray) {
            requiredDimensions.put(key, oneValue.toString());
          }
        } else {
          requiredDimensions.put(key, valueObj.toString());
        }
      }
    }
    return new JsonTestbedConfig(
        name,
        devices.buildOrThrow(),
        dimensions.build(),
        requiredDimensions.build(),
        properties.buildOrThrow());
  }
}
