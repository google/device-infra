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

package com.google.devtools.mobileharness.platform.testbed.config;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.flogger.FluentLogger;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;

/**
 * A {@link Representer} that allows for snakeyaml serialization of any {@link TestbedConfig} to a
 * YAML v1.1 representation.
 *
 * <p>The specific schema for serialization is described at go/mh-testbeds-static.
 */
final class TestbedConfigYamlRepresenter extends Representer {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public TestbedConfigYamlRepresenter() {
    super(new DumperOptions());
    this.multiRepresenters.put(TestbedConfig.class, new RepresentTestbedConfig());
    this.multiRepresenters.put(SubDeviceInfo.class, new RepresentSubDeviceInfo());
    this.multiRepresenters.put(JSONObject.class, new RepresentJsonObject());
    this.multiRepresenters.put(JSONArray.class, new RepresentJsonArray());
  }

  /** Represents {@link TestbedConfig} as YAML strings using only native (i.e. safe) types. */
  private final class RepresentTestbedConfig implements Represent {
    @Override
    public Node representData(Object data) {
      TestbedConfig config = (TestbedConfig) data;
      ImmutableMap.Builder<String, Object> configMap = new ImmutableMap.Builder<>();
      configMap.put("name", config.getName());
      configMap.put("devices", config.getDevices().values().asList());
      if (!config.getDimensions().isEmpty()) {
        configMap.put("dimensions", config.getDimensions());
      }
      if (!config.getProperties().isEmpty()) {
        configMap.put("properties", config.getProperties());
      }

      return represent(ImmutableList.of(configMap.buildOrThrow()));
    }
  }

  /**
   * Represents {@link SubDeviceInfo} as YAML strings using only native (i.e. safe) types.
   *
   * <p>Use with caution! The writing of a SubDeviceInfo dimensions field to YAML can result in the
   * loss of dimensions values if the same key has multiple values.
   */
  private final class RepresentSubDeviceInfo implements Represent {
    @Override
    public Node representData(Object data) {
      SubDeviceInfo device = (SubDeviceInfo) data;
      ImmutableMap.Builder<String, Object> deviceConfigMap = new ImmutableMap.Builder<>();
      deviceConfigMap.put("id", device.getId());
      deviceConfigMap.put("type", device.getDeviceType().getSimpleName());

      ImmutableList<String> aliases = device.getDeviceAliasType().asList();
      if (!aliases.isEmpty()) {
        deviceConfigMap.put("aliases", aliases);
      }

      ImmutableMultimap<String, String> dimensions = device.getDimensions();
      if (!dimensions.isEmpty()) {
        Map<String, String> dimensionMap = new HashMap<>();
        // If multiple values exist for the same key, this will only preserve the first such
        // key-value pair.
        for (Map.Entry<String, String> entry : dimensions.entries()) {
          if (dimensionMap.containsKey(entry.getKey())) {
            logger.atWarning().log(
                "YAML does not support multiple values for subdevice dimension keys! "
                    + "Dropping dimension %s:%s for subdevice %s.",
                entry.getKey(), entry.getValue(), device.getId());
            continue;
          }
          dimensionMap.put(entry.getKey(), entry.getValue());
        }
        deviceConfigMap.put("dimensions", dimensionMap);
      }
      ImmutableMap<String, ?> properties = device.getProperties();
      if (!properties.isEmpty()) {
        deviceConfigMap.put("properties", properties);
      }
      return represent(deviceConfigMap.buildOrThrow());
    }
  }

  /** Represents {@link JSONObject} as YAML strings using only native (i.e. safe) types. */
  private final class RepresentJsonObject implements Represent {
    @Override
    public Node representData(Object data) {
      JSONObject jsonObject = (JSONObject) data;
      ImmutableMap.Builder<String, Object> jsonObjectMap = new ImmutableMap.Builder<>();
      // TODO: This awkward for-loop is a workaround for JSONObject#keys returning a raw
      // type (Iterator instead of Iterator<?>) and avoiding javac warnings. It should be replaced
      // with something more readable (i.e., a stream).
      for (Object key : (Iterable) jsonObject::keys) {
        String stringKey = key.toString();
        jsonObjectMap.put(stringKey, jsonObject.opt(stringKey));
      }
      return represent(jsonObjectMap.buildOrThrow());
    }
  }

  /** Represents {@link JSONArray} as YAML strings using only native (i.e. safe) types. */
  private final class RepresentJsonArray implements Represent {
    @Override
    public Node representData(Object data) {
      return represent(
          IntStream.range(0, ((JSONArray) data).length())
              .boxed()
              .map(((JSONArray) data)::opt)
              .collect(toImmutableList()));
    }
  }
}
