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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.testbed.config.BaseSubDeviceInfo;
import com.google.devtools.mobileharness.platform.testbed.config.SubDeviceInfo;
import com.google.wireless.qa.mobileharness.shared.api.ClassUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** An implementation of {@link SubDeviceInfo} that loads device info from a {@link JSONObject}. */
public class JsonSubDeviceInfo extends BaseSubDeviceInfo {

  private static final String ID_KEY = "id";
  private static final String TYPE_NAME_KEY = "type";
  private static final String ALIAS_NAME_KEY = "aliases";
  private static final String PROPERTIES_KEY = "properties";
  private static final String DIMENSIONS_KEY = "dimensions";
  private static final String MISC_TESTBED_SUBDEVICE_CLASS = "MiscTestbedSubDevice";
  private static final String MOBLY_TYPE_DIMENSION_KEY = "mobly_type";

  private JsonSubDeviceInfo(
      String id,
      Class<? extends Device> deviceType,
      Set<String> deviceAliases,
      Multimap<String, String> dimensions,
      Map<String, Object> properties)
      throws MobileHarnessException {
    super(id, deviceType, deviceAliases, dimensions, properties);
  }

  /**
   * Creates device info from a json object.
   *
   * @param jsonObject The json object to create the device info from.
   * @throws MobileHarnessException Thrown if the device info could not be loaded.
   */
  public static JsonSubDeviceInfo create(
      JSONObject jsonObject, String testbedName, int subdeviceIndex)
      throws MobileHarnessException, JSONException {
    String typeName;
    if (jsonObject.has(TYPE_NAME_KEY)) {
      typeName = jsonObject.getString(TYPE_NAME_KEY);
    } else {
      throw new MobileHarnessException(
          ExtErrorId.TESTBED_PARSING_TYPE_NOT_EXIST_ERROR,
          "Device requires a type field.\n" + jsonObject);
    }

    String id;
    if (jsonObject.has(ID_KEY)) {
      id = jsonObject.get(ID_KEY).toString();
    } else {
      if (!MISC_TESTBED_SUBDEVICE_CLASS.equals(typeName)) {
        throw new MobileHarnessException(
            ExtErrorId.TESTBED_PARSING_ID_NOT_EXIST_ERROR,
            "Device requires an id field.\n" + jsonObject);
      }
      if (!jsonObject.has(DIMENSIONS_KEY)) {
        throw new MobileHarnessException(
            ExtErrorId.TESTBED_PARSING_TYPE_NOT_EXIST_ERROR,
            "Misc sub device must have dimensions with mobly_type.\n" + jsonObject);
      }

      JSONObject subDeviceDimensions = jsonObject.getJSONObject(DIMENSIONS_KEY);
      if (!subDeviceDimensions.has(MOBLY_TYPE_DIMENSION_KEY)) {
        throw new MobileHarnessException(
            ExtErrorId.TESTBED_PARSING_TYPE_NOT_EXIST_ERROR,
            "Misc sub device dimensions must have mobly_type.\n" + jsonObject);
      }
      id =
          String.format(
              "%s-%s-%s",
              testbedName,
              subDeviceDimensions.getString(MOBLY_TYPE_DIMENSION_KEY),
              subDeviceDimensions.has(ID_KEY)
                  ? subDeviceDimensions.getString(ID_KEY)
                  : subdeviceIndex);
    }

    JSONArray aliases;
    if (jsonObject.has(ALIAS_NAME_KEY)) {
      aliases = jsonObject.getJSONArray(ALIAS_NAME_KEY);
    } else {
      aliases = new JSONArray();
    }
    ImmutableSet.Builder<String> deviceAliasesBuilder = ImmutableSet.builder();
    for (int i = 0; i < aliases.length(); i++) {
      deviceAliasesBuilder.add(aliases.getString(i));
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

    ImmutableMultimap.Builder<String, String> dimensions = new ImmutableMultimap.Builder<>();
    if (jsonObject.has(DIMENSIONS_KEY)) {
      JSONObject jsonDimensions = jsonObject.getJSONObject(DIMENSIONS_KEY);
      Iterator<?> keyIt = jsonDimensions.keys();
      while (keyIt.hasNext()) {
        String key = keyIt.next().toString();
        dimensions.put(key, jsonDimensions.get(key).toString());
      }
    }
    return new JsonSubDeviceInfo(
        id,
        ClassUtil.getDeviceClass(typeName),
        deviceAliasesBuilder.build(),
        dimensions.build(),
        properties.buildOrThrow());
  }
}
