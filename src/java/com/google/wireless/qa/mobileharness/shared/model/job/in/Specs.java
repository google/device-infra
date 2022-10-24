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

package com.google.wireless.qa.mobileharness.shared.model.job.in;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.wireless.qa.mobileharness.shared.model.job.in.json.ProtocolMessageOrBuilderJsonSerializer;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.util.List;

/**
 * A java version of protobuf {@link JobSpec}. Used during the process of deprecate Specs. It should
 * at last be replaced by protobuf.
 */
public class Specs {

  private final JsonObject json;

  /** Global specs that need to merge. */
  private final Params globalParam;

  public Specs() {
    this(new JsonObject(), new Params((Timing) null));
  }

  public Specs(JsonObject json) {
    this(json, new Params((Timing) null));
  }

  public Specs(JsonObject json, Params globalParam) {
    this.json = json;
    this.globalParam = globalParam;
  }

  public String getString(String name, String defaultValue) {
    if (json.has(name)) {
      return jsonToString(json.get(name));
    }
    return globalParam.get(name, defaultValue);
  }

  /**
   * Gets a boolean from the parameter.
   *
   * @param name parameter name/key
   * @param defaultValue the default boolean value if the parameter does not exist or is empty
   */
  public boolean getBool(String name, boolean defaultValue) {
    if (json.has(name)) {
      return json.get(name).getAsBoolean();
    }
    return globalParam.getBool(name, defaultValue);
  }

  /**
   * Gets an integer from the parameter.
   *
   * @param name parameter name/key.
   * @param defaultValue the default int value if the param not exists, or the param value is not
   *     valid
   */
  public int getInt(String name, int defaultValue) {
    if (json.has(name)) {
      return json.get(name).getAsInt();
    }
    return globalParam.getInt(name, defaultValue);
  }

  /**
   * Gets a double from the parameter.
   *
   * @param name parameter name/key.
   * @param defaultValue the default int value if the param not exists, or the param value is not
   *     valid
   */
  public double getDouble(String name, double defaultValue) {
    if (json.has(name)) {
      return json.get(name).getAsDouble();
    }
    return globalParam.getDouble(name, defaultValue);
  }

  /**
   * Gets the comma separated value list from the parameter.
   *
   * @param name parameter name/key.
   * @param defaultValue the default if the param does not exist, or the param value is not valid
   */
  public List<String> getList(String name, List<String> defaultValue) {
    if (json.has(name)) {
      JsonArray jsonArray = json.get(name).getAsJsonArray();
      ImmutableList.Builder<String> builder = ImmutableList.builder();
      for (int i = 0; i < jsonArray.size(); i++) {
        builder.add(jsonToString(jsonArray.get(i)));
      }
      return builder.build();
    }
    return globalParam.getList(name, defaultValue);
  }

  /** Gets current specs to a Json. */
  public JsonObject asJson() {
    return json;
  }

  /**
   * Gets current specs as an object of java class {@code clazz}.
   *
   * @throws MobileHarnessException if failed to convert specs to class {@code clazz}
   */
  public <T> T asObject(Class<T> clazz) throws MobileHarnessException {
    try {
      return new Gson().fromJson(json, clazz);
    } catch (JsonSyntaxException e) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_SPEC_PARSE_JSON_ERROR,
          String.format("Failed to get scoped param as class %s. param: %s", clazz, json),
          e);
    }
  }

  /**
   * Gets specs as a message of protobuf {@code message}.
   *
   * @throws MobileHarnessException if failed to convert specs to class {@code message}
   */
  @SuppressWarnings("unchecked")
  public <T extends Message> T asMessage(Class<T> message) throws MobileHarnessException {
    try {
      ParamsJobSpec paramsJobSpec = new ParamsJobSpec(globalParam, true);
      T messageFromParams = paramsJobSpec.getSpec(message);

      T fromJson = MessageGsonHolder.gson.fromJson(json, message);

      return (T) messageFromParams.toBuilder().mergeFrom(fromJson).build();
    } catch (JsonSyntaxException e) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_SPEC_PARSE_JSON_ERROR,
          String.format("Failed to get scoped param as message %s. param: %s", message, json),
          e);
    }
  }

  /** Gets value of {@code name} as a specs. */
  public Specs getSpecs(String name) {
    return new Specs(json.get(name).getAsJsonObject());
  }

  private String jsonToString(JsonElement json) {
    if (json.isJsonPrimitive()) {
      return json.getAsString();
    }
    return json.toString();
  }

  static final class MessageGsonHolder {

    /** Gson for serialize/deserialize Protobuf message. It's thread-safe. */
    static final Gson gson =
        new GsonBuilder()
            .registerTypeHierarchyAdapter(
                MessageOrBuilder.class, new ProtocolMessageOrBuilderJsonSerializer(false, true))
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private MessageGsonHolder() {}
  }
}
