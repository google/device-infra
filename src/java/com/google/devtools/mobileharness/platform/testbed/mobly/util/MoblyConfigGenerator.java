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

package com.google.devtools.mobileharness.platform.testbed.mobly.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.testbed.adhoc.AdhocTestbedConfig;
import com.google.devtools.mobileharness.platform.testbed.config.SubDeviceInfo;
import com.google.devtools.mobileharness.platform.testbed.config.SubDeviceKey;
import com.google.devtools.mobileharness.platform.testbed.config.TestbedConfig;
import com.google.devtools.mobileharness.platform.testbed.mobly.MoblyConstant;
import com.google.devtools.mobileharness.platform.testbed.mobly.MoblyConstant.ConfigKey;
import com.google.wireless.qa.mobileharness.shared.api.ClassUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.device.SimpleCompositeDevice;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Optional;
import javax.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Provides a utility to generate a Mobly test config for supported device types. */
public class MoblyConfigGenerator {

  // TODO: Update config generation to not rely on passing aroundsomething besides
  // org.json types.

  /** Prefix to attach to device ids for configs generated from non-{@link TestbedDevice}. */
  private static final String TESTBED_PREFIX = "testbed_";

  private MoblyConfigGenerator() {}

  /**
   * Gets a JSONObject config which represents controller and test param information locally defined
   * on the lab server.
   *
   * @param device The {@link Device} object to create a Mobly testbed config for.
   * @return a JSONObject config in Mobly testbed config format.
   * @throws JSONException If there are any issues manipulating the config itself
   * @throws MobileHarnessException If there are issues identifying subdevice type
   */
  public static JSONObject getLocalMoblyConfig(Device device) throws MobileHarnessException {
    // TODO: Support static Testbed device.
    if (device instanceof SimpleCompositeDevice) {
      SimpleCompositeDevice simpleCompositeDevice = (SimpleCompositeDevice) device;
      return buildLocalMoblyConfig(
          simpleCompositeDevice.getDeviceId(),
          AdhocTestbedConfig.create(
              simpleCompositeDevice.getDeviceId(),
              simpleCompositeDevice.getManagedDevices().asList()));
    } else {
      // If not a testbed device (i.e, a single physical device), create a temporary
      // AdhocTestbedConfig object with a prefix because a testbed subdevice cannot have the same is
      // as the testbed itself.
      String testbedName = TESTBED_PREFIX + sanitizeTestbedName(device.getDeviceId());
      return buildLocalMoblyConfig(
          testbedName, AdhocTestbedConfig.create(testbedName, Arrays.asList(device)));
    }
  }

  /**
   * Builds the testbed config JSONObject that represents the controller + test param information
   * locally defined on the lab server.
   */
  private static JSONObject buildLocalMoblyConfig(String testbedName, TestbedConfig testbedConfig)
      throws JSONException, MobileHarnessException {
    JSONObject moblyLocalConfig = new JSONObject();

    // Copy the testbed name
    moblyLocalConfig.put(MoblyConstant.ConfigKey.TESTBED_NAME, testbedName);

    // Load generic testbed properties into test params scope
    JSONObject testParams = new JSONObject();
    for (Entry<String, Object> property : testbedConfig.getProperties().entrySet()) {
      testParams.put(property.getKey(), property.getValue());
    }

    JSONObject testbedDimensions = new JSONObject();
    for (Entry<String, String> dimension : testbedConfig.getDimensions().entrySet()) {
      testbedDimensions.put(dimension.getKey(), dimension.getValue());
    }
    testParams.put(ConfigKey.TEST_PARAM_MH_DIMENSIONS, testbedDimensions);
    moblyLocalConfig.put(MoblyConstant.ConfigKey.TEST_PARAMS, testParams);

    // Load subdevice info into controller scope
    for (Entry<SubDeviceKey, SubDeviceInfo> subDevice : testbedConfig.getDevices().entrySet()) {
      MoblySubdeviceType type =
          getTypeFromClassName(subDevice.getKey().deviceType().getSimpleName());
      // Create a subdevice config
      JSONObject subDeviceConfig =
          createSubDeviceConfig(subDevice.getKey().deviceId(), type, subDevice.getValue());
      // Concatenate onto the main config
      concatMoblyConfig(moblyLocalConfig, subDeviceConfig);
    }

    return moblyLocalConfig;
  }

  /**
   * Creates a testbed config containing a single subdevice.
   *
   * @param id the id of the single subdevice
   * @param type the subdevice type
   * @param subDeviceInfo any additional information (e.g., dimensions or properties) that should be
   *     associated at the subdevice (rather than testbed) level of the config.
   * @return A JSONObject in Mobly config format representing a single testbed.
   * @throws JSONException If there are issues manipulating the config
   */
  private static JSONObject createSubDeviceConfig(
      String id, MoblySubdeviceType type, @Nullable SubDeviceInfo subDeviceInfo)
      throws MobileHarnessException, JSONException {
    JSONObject controller = new JSONObject();
    if (subDeviceInfo != null) {
      for (Entry<String, ?> property : subDeviceInfo.getProperties().entrySet()) {
        // Mobly test configs and generic testbed configs are json/yaml but TestbedLoaders can load
        // any object type into properties. Try first to set the property as is, but if that fails
        // then try reading the toString as a JSON supported type.
        try {
          controller.put(property.getKey(), property.getValue());
        } catch (JSONException e) {
          controller.put(
              property.getKey(), new JSONTokener(property.getValue().toString()).nextValue());
        }
      }
      JSONObject jsonDimensions = new JSONObject();
      for (String key : subDeviceInfo.getDimensions().keySet()) {
        if (subDeviceInfo.getDimensions().get(key).size() == 1) {
          jsonDimensions.put(key, subDeviceInfo.getDimensions().get(key).iterator().next());
        } else {
          jsonDimensions.put(key, new JSONArray(subDeviceInfo.getDimensions().get(key)));
        }
      }
      controller.put(MoblyConstant.ConfigKey.MOBILE_HARNESS_DIMENSIONS, jsonDimensions);
    }
    controller.put(type.getJsonIdKey(), id);
    String moblyType = type.getJsonTypeName();
    // Only for Miscellaneous subdevice types (usually hardware not supported by Mobile Harness)
    // grab the controller type from properties.
    if (type.equals(MoblySubdeviceType.MISC_TESTBED_SUB_DEVICE) && subDeviceInfo != null) {
      ImmutableCollection<String> moblyTypeStrings =
          subDeviceInfo
              .getDimensions()
              .get(MoblySubdeviceType.MISC_TESTBED_SUB_DEVICE.getJsonTypeName());
      if (moblyTypeStrings.size() != 1) {
        throw new MobileHarnessException(
            ExtErrorId.MOBLY_MISC_TESTBED_SUBDEVICE_JSON_TYPE_NAME_ERROR,
            String.format(
                "Exactly one value for key \"%s\" is expected in subdevice dimensions, found %s.",
                MoblySubdeviceType.MISC_TESTBED_SUB_DEVICE.getJsonTypeName(),
                moblyTypeStrings.size()));
      }
      moblyType = moblyTypeStrings.asList().get(0);
      // Remove this from the Controller config for MiscTestbedSubDevice.
      controller.remove(MoblySubdeviceType.MISC_TESTBED_SUB_DEVICE.getJsonIdKey());
    }

    JSONObject config = new JSONObject();
    // Mobly objects to characters in the testbed name other than alphanumerics, "-", "_", and ".".
    // Use a regex to replace all other characters in the testbed name.
    String testbedName = sanitizeTestbedName(id);
    config.put(MoblyConstant.ConfigKey.TESTBED_NAME, testbedName);

    JSONObject controllerConfigs = new JSONObject();
    controllerConfigs.put(moblyType, new JSONArray());
    controllerConfigs.getJSONArray(moblyType).put(controller);
    config.put(MoblyConstant.ConfigKey.TESTBED_CONTROLLERS, controllerConfigs);
    return config;
  }

  /** Infers the MoblySubdeviceType from the device className. */
  private static MoblySubdeviceType getTypeFromClassName(String className)
      throws MobileHarnessException {
    Optional<MoblySubdeviceType> finalType = Optional.empty();
    Class<? extends Device> subdeviceClass = ClassUtil.getDeviceClass(className);
    for (MoblySubdeviceType subdeviceType : MoblySubdeviceType.values()) {
      Class<? extends Device> typeClass = ClassUtil.getDeviceClass(subdeviceType.getMhClassName());
      if (!typeClass.isAssignableFrom(subdeviceClass)) {
        continue;
      }
      if (finalType.isEmpty()
          || ClassUtil.getDeviceClass(finalType.get().getMhClassName())
              .isAssignableFrom(typeClass)) {
        finalType = Optional.of(subdeviceType);
      }
    }
    if (finalType.isPresent()) {
      return finalType.get();
    }
    throw new MobileHarnessException(
        ExtErrorId.MOBLY_SUBDEVICE_TYPE_NOT_FOUND_ERROR,
        "Could not find a subdevice type for class " + className);
  }

  private static String sanitizeTestbedName(String name) {
    return name.replaceAll("[^a-zA-Z0-9\\.\\-_]", "");
  }

  /**
   * Concatenates JSON configs in the Mobly Testbed format
   *
   * <p>As an example when a first mobly config: * { "TestParams":{ "param_a": "hello", "param_b":
   * "world" }, "Controllers": { "controller_a": [{"foo":"1234567"}, ["bar", "baz"]] }
   *
   * <p>is concatenated with the following: { "TestParams":{ "param_b": "david", "param_c": "hi" },
   * "Controllers": { "controller_a": ["zyxwv"], "controller_b": ["abcde"] } }
   *
   * <p>the result is: { "TestParams":{ "param_a": "hello", "param_b": "world", "param_c": "hi" },
   * "Controllers": { "controller_a": [{"foo":"1234567"}, ["bar", "baz"], "zyxwv"], "controller_b":
   * ["abcde"] } }
   *
   * <p>Notice that
   *
   * <ol>
   *   <li>controller configs always consists of String-JSONArray entries and are concatenated *in
   *       order*,
   *   <li>WARNING: There is validity check for controller config formats; this method simply
   *       concatenates controller entries. It is possible after concatenation that a controller
   *       list contains a mix of JSON types. This can result in a possibly invalid format for
   *       downstream consumer of the config.
   *   <li>existing test parameters are not overwritten,
   *   <li>new test parameters are added,
   *   <li>any entries besides controllers and test params (e.g., MH Dimensions) are ignored.
   * </ol>
   *
   * @param originalConfig JSON config of one testbed instance (see {@link
   *     MoblyConfigUtil#readTestbedModelConfigs()})
   * @param addedConfig JSON config of a second testbed instance (see {@link
   *     MoblyConfigUtil#readTestbedModelConfigs()})
   */
  @VisibleForTesting
  static void concatMoblyConfig(JSONObject originalConfig, JSONObject addedConfig)
      throws JSONException {
    // Concatenate the test params
    JSONObject testParams = (JSONObject) originalConfig.opt(MoblyConstant.ConfigKey.TEST_PARAMS);
    JSONObject addedTestParams = (JSONObject) addedConfig.opt(MoblyConstant.ConfigKey.TEST_PARAMS);
    // If the original config did not have test params and the added one does, create a JSONObject
    // to copy entries into.
    if (addedTestParams != null) {
      if (testParams == null) {
        originalConfig.put(MoblyConstant.ConfigKey.TEST_PARAMS, new JSONObject());
        testParams = originalConfig.getJSONObject(MoblyConstant.ConfigKey.TEST_PARAMS);
      }
      Iterator<?> paramKeys = addedTestParams.keys();
      while (paramKeys.hasNext()) {
        String key = (String) paramKeys.next();
        // If the key doesn't exist, add it.
        if (!testParams.has(key)) {
          testParams.put(key, addedTestParams.get(key));
        }
      }
    }

    // Concatenate the controllers
    JSONObject controllers =
        (JSONObject) originalConfig.opt(MoblyConstant.ConfigKey.TESTBED_CONTROLLERS);
    JSONObject addedControllers =
        (JSONObject) addedConfig.opt(MoblyConstant.ConfigKey.TESTBED_CONTROLLERS);
    // If the original config did not have test params and the added one does, create a JSONObject
    // to copy entries into.
    if (addedControllers != null) {
      if (controllers == null && addedControllers != null) {
        originalConfig.put(MoblyConstant.ConfigKey.TESTBED_CONTROLLERS, new JSONObject());
        controllers = originalConfig.getJSONObject(MoblyConstant.ConfigKey.TESTBED_CONTROLLERS);
      }
      Iterator<?> controllerKeys = addedControllers.keys();
      while (controllerKeys.hasNext()) {
        String key = (String) controllerKeys.next();
        // If the key doesn't exist, add it.
        if (!controllers.has(key)) {
          controllers.put(key, addedControllers.get(key));
        } else {
          if (controllers.get(key) instanceof JSONArray
              && addedControllers.get(key) instanceof JSONArray) {
            JSONArray original = controllers.getJSONArray(key);
            JSONArray added = addedControllers.getJSONArray(key);
            for (int i = 0; i < added.length(); i++) {
              original.put(added.get(i));
            }
          }
        }
      }
    }
  }
}
