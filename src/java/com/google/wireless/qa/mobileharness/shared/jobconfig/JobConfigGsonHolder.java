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

package com.google.wireless.qa.mobileharness.shared.jobconfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.google.protobuf.MessageOrBuilder;
import com.google.wireless.qa.mobileharness.shared.model.job.in.json.ProtocolMessageOrBuilderJsonSerializer;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Static utility class for {@link JobConfig} gson object. */
public final class JobConfigGsonHolder {

  /** Gson for serialize/deserialize {@link JobConfig}. It's thread-safe. */
  private static final Gson gson =
      new GsonBuilder()
          .registerTypeAdapter(JobConfig.StringMap.class, new StringMapSerializer())
          .registerTypeAdapter(JobConfig.StringList.class, new StringListSerializer())
          .registerTypeAdapter(JobConfig.FileConfigList.class, new FileConfigListSerializer())
          .registerTypeAdapter(JobConfig.Driver.class, new DriverSerializer())
          .registerTypeAdapter(JobConfig.DecoratorList.class, new DecoratorListSerializer())
          .registerTypeAdapterFactory(new SubDeviceSpecAdapterFactory())
          .registerTypeAdapterFactory(new DeviceListAdapterFactory())
          .registerTypeHierarchyAdapter(
              MessageOrBuilder.class, new ProtocolMessageOrBuilderJsonSerializer())
          .setPrettyPrinting()
          .disableHtmlEscaping()
          .create();

  /** Gets default gson instance for convert between json and {@link JobConfig}. */
  public static Gson getGson() {
    return gson;
  }

  /**
   * Converts {@code json} to a better format. Returns the original string if it is not a valid
   * json.
   */
  public static String prettyJson(String json) {
    try {
      return getGson().toJson(JsonParser.parseString(json));
    } catch (Throwable t) {
      return json;
    }
  }

  /**
   * Converts {@code element} to a string. It's more restricted than the Gson convert. In Gson, it
   * is valid to convert a one-dimension array to a string, however we don't allow such conversion.
   * Throws {@link IllegalStateException} to keep consistent with Gson building methods.
   *
   * @throws IllegalStateException if element is not a valid string
   */
  private static String getAsString(JsonElement element) {
    if (!element.isJsonPrimitive()) {
      throw new IllegalStateException("Expect a string, but get: " + element);
    }
    return element.getAsString();
  }

  /**
   * Serializer to convert {@link JobConfig.StringList} to a json array. For example:
   *
   * <p>Proto form: <code>
   *    content: "a"
   *    content: "b"
   *    content: "c"
   * </code>
   *
   * <p>Json form: <code>
   *    ["a", "b", "c"]
   * </code>
   */
  private static class StringListSerializer
      implements JsonDeserializer<JobConfig.StringList>, JsonSerializer<JobConfig.StringList> {

    @Override
    public JobConfig.StringList deserialize(
        JsonElement jsonElement, Type type, JsonDeserializationContext context) {
      JobConfig.StringList.Builder stringList = JobConfig.StringList.newBuilder();
      try {
        for (JsonElement item : jsonElement.getAsJsonArray()) {
          stringList.addContent(getAsString(item));
        }
      } catch (IllegalStateException e) {
        throw new JsonParseException(
            "String List is in the form of\n"
                + "  [\"a\", \"b\", \"c\"],\n"
                + "But get\n  "
                + getGson().toJson(jsonElement),
            e);
      }
      return stringList.build();
    }

    @Override
    public JsonElement serialize(
        JobConfig.StringList stringList, Type type, JsonSerializationContext context) {
      return context.serialize(stringList.getContentList());
    }
  }

  /**
   * Serializer to convert {@link JobConfig.StringMap} to a json array. For example:
   * <p> Proto form: <code>
   *    content {
   *      key: "key1"
   *      value: "value1"
   *    }
   *    content {
   *      key: "key2"
   *      value: "value2"
   *    }
   * </code>
   *
   * <p>Json form: <code>
   *    {
   *      "key1": "value1"
   *      "key2": "value2"
   *    }
   */
  private static class StringMapSerializer
      implements JsonSerializer<JobConfig.StringMap>, JsonDeserializer<JobConfig.StringMap> {

    @Override
    public JobConfig.StringMap deserialize(
        JsonElement jsonElement, Type type, JsonDeserializationContext context) {
      JobConfig.StringMap.Builder stringMap = JobConfig.StringMap.newBuilder();
      try {
        for (Map.Entry<String, JsonElement> entry : jsonElement.getAsJsonObject().entrySet()) {
          stringMap.putContent(entry.getKey(), getAsString(entry.getValue()));
        }
      } catch (IllegalStateException e) {
        throw new JsonParseException(
            "String Map should in the form of\n"
                + "  {\n"
                + "   \"key1\": \"value1\"\n"
                + "   \"key2\": \"value2\"\n"
                + "  }\n"
                + "But get:\n  "
                + getGson().toJson(jsonElement),
            e);
      }
      return stringMap.build();
    }

    @Override
    public JsonElement serialize(
        JobConfig.StringMap stringMap, Type type, JsonSerializationContext context) {
      return context.serialize(stringMap.getContentMap());
    }
  }

  /**
   * Serializer to convert {@link JobConfig.FileConfigList} to a json array. For example:
   *
   * <p>Proto form: <code>
   *    content {
   *      tag: "tag1"
   *      paths: "path1_1"
   *      paths: "path1_2"
   *    }
   *    content {
   *      tag: "tag2"
   *      paths: "path2_1"
   *      paths: "path2_2"
   *    }
   * </code>
   *
   * <p>Json form: <code>
   *    {
   *      "tag1": ["path1_1", "path1_2"]
   *      "tag2": ["path2_1", "path2_2"]
   *    }
   * </code>
   */
  private static class FileConfigListSerializer
      implements JsonSerializer<JobConfig.FileConfigList>,
          JsonDeserializer<JobConfig.FileConfigList> {

    @Override
    public JobConfig.FileConfigList deserialize(
        JsonElement jsonElement, Type type, JsonDeserializationContext context) {
      JobConfig.FileConfigList.Builder fileConfigList = JobConfig.FileConfigList.newBuilder();
      try {
        // The order of entry set is preserved by Gson.
        for (Map.Entry<String, JsonElement> entry : jsonElement.getAsJsonObject().entrySet()) {
          JobConfig.FileConfigList.FileConfig.Builder fileConfig =
              fileConfigList.addContentBuilder();
          fileConfig.setTag(entry.getKey());
          for (JsonElement path : entry.getValue().getAsJsonArray()) {
            fileConfig.addPath(path.getAsString());
          }
        }
      } catch (IllegalStateException e) {
        throw new JsonParseException(
            "File Config List should in the form of:\n"
                + "  {\n"
                + "    \"tag1\": [\"path1_1\", \"path1_2\"]\n"
                + "    \"tag2\": [\"path2_1\", \"path2_2\"]\n"
                + "  }\n"
                + "But get:\n  "
                + getGson().toJson(jsonElement),
            e);
      }
      return fileConfigList.build();
    }

    @Override
    public JsonElement serialize(
        JobConfig.FileConfigList fileConfigList, Type type, JsonSerializationContext context) {
      JsonObject result = new JsonObject();
      for (JobConfig.FileConfigList.FileConfig config : fileConfigList.getContentList()) {
        result.add(config.getTag(), context.serialize(config.getPathList()));
      }
      return result;
    }
  }

  private static class DeviceListAdapterFactory implements TypeAdapterFactory {

    @Nullable
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
      if (!type.getRawType().equals(JobConfig.DeviceList.class)) {
        return null;
      }
      TypeAdapter<T> deviceListDelegate = gson.getDelegateAdapter(this, type);
      TypeAdapter<JobConfig.SubDeviceSpec> subDeviceDelegate =
          gson.getDelegateAdapter(this, TypeToken.get(JobConfig.SubDeviceSpec.class));
      return new TypeAdapter<T>() {
        @Override
        public void write(JsonWriter out, T value) throws IOException {
          deviceListDelegate.write(out, value);
        }

        @Override
        public T read(JsonReader in) throws IOException {
          if (in.peek() == JsonToken.BEGIN_ARRAY) {
            in.beginArray();
            List<JobConfig.SubDeviceSpec> specs = new ArrayList<>();
            while (in.hasNext() && in.peek() != JsonToken.END_ARRAY) {
              specs.add(subDeviceDelegate.read(in));
            }
            in.endArray();
            @SuppressWarnings("unchecked")
            // This cast is safe because the factory rejects anything other that T == DeviceList
            T ret = (T) JobConfig.DeviceList.newBuilder().addAllSubDeviceSpec(specs).build();
            return ret;
          }
          return deviceListDelegate.read(in);
        }
      };
    }
  }

  private static class SubDeviceSpecAdapterFactory implements TypeAdapterFactory {

    @Nullable
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
      if (!type.getRawType().equals(JobConfig.SubDeviceSpec.class)) {
        return null;
      }
      TypeAdapter<T> subDeviceSpecDelegate = gson.getDelegateAdapter(this, type);
      TypeAdapter<JobConfig.StringMap> stringMapDelegate =
          gson.getDelegateAdapter(this, TypeToken.get(JobConfig.StringMap.class));
      TypeAdapter<JobConfig.DecoratorList> decoratorListDelegate =
          gson.getDelegateAdapter(this, TypeToken.get(JobConfig.DecoratorList.class));

      return new TypeAdapter<T>() {
        @Override
        public void write(JsonWriter out, T value) throws IOException {
          subDeviceSpecDelegate.write(out, value);
        }

        @Override
        public T read(JsonReader in) throws IOException {
          if (in.peek() == JsonToken.BEGIN_ARRAY) {
            in.beginArray();
            JobConfig.SubDeviceSpec.Builder builder = JobConfig.SubDeviceSpec.newBuilder();

            if (in.peek() != JsonToken.STRING) {
              wrongFormat();
            }
            builder.setType(in.nextString());
            if (in.peek() != JsonToken.BEGIN_OBJECT) {
              wrongFormat();
            }
            JobConfig.StringMap dimensions = stringMapDelegate.read(in);
            if (dimensions.getContentCount() > 0) {
              builder.setDimensions(dimensions);
            }
            if (in.peek() != JsonToken.BEGIN_ARRAY) {
              wrongFormat();
            }
            builder.setDecorators(decoratorListDelegate.read(in));

            in.endArray();
            @SuppressWarnings("unchecked")
            // This cast is safe because the factory rejects anything other that T == SubDeviceSpec
            T ret = (T) builder.build();
            return ret;
          }
          if (in.peek() == JsonToken.STRING) {
            @SuppressWarnings("unchecked")
            // This cast is safe because the factory rejects anything other that T == SubDeviceSpec
            T ret = (T) JobConfig.SubDeviceSpec.newBuilder().setType(in.nextString()).build();
            return ret;
          }
          return subDeviceSpecDelegate.read(in);
        }

        private void wrongFormat() {
          throw new JsonParseException(
              "If using the shorthand syntax, JobConfig.SubDeviceSpec can only parse from a "
                  + "three-item array of String, Object, then List. e.g. ("
                  + "    \"AndroidRealDevice\","
                  + "    {\"label\": \"foo\"},"
                  + "    [(\"Decorator1\", {\"foo\": \"bar\"})]).");
        }
      };
    }
  }

  /**
   * Serializer to convert {@link JobConfig.Driver} to a string or a json array.
   *
   * <p> A Driver with no params will be serialized to or de-serialized from a string. For example:
   *
   * <p> Proto form: <code>
   *    name: "AndroidDriver"
   * </code>
   *
   * <p> Json form: <code>
   *    "AndroidDriver"
   * </code>
   *
   * <p> A driver with params will be serialized to a two-item array, with the first item is the
   * name, and the second item is the json form of param. For example:
   *
   * <p> Proto form: <code>
   *    name: "AndroidDriver"
   *    param: "{\"aaa\":\"bbb\"}"
   * </code>
   *
   * <p> Json form: <code>
   *    [
   *       "AndroidDriver",
   *       {
   *          "aaa": "bbb"
   *       }
   *    ]
   * Notice that an exception will be throwed if the Driver params is not a valid json string.
   */
  private static class DriverSerializer
      implements JsonSerializer<JobConfig.Driver>, JsonDeserializer<JobConfig.Driver> {

    @Override
    public JobConfig.Driver deserialize(
        JsonElement json, Type typeOfT, JsonDeserializationContext context) {
      JobConfig.Driver.Builder driver = JobConfig.Driver.newBuilder();
      if (json.isJsonPrimitive()) {
        driver.setName(json.getAsString());
      } else if (json.isJsonArray()) {
        JsonArray array = json.getAsJsonArray();
        if (array.size() != 2) {
          throw new JsonParseException("JobConfig.Driver could only parse from a two-item array.");
        }
        driver.setName(array.get(0).getAsString());
        if (!array.get(1).isJsonObject()) {
          throw new JsonParseException(
              "JobConfig.Driver param must be a JsonObject, but found: " + array.get(1));
        }
        driver.setParam(array.get(1).toString());
      }
      return driver.build();
    }

    @Override
    public JsonElement serialize(
        JobConfig.Driver driver, Type typeOfSrc, JsonSerializationContext context) {
      if (driver.hasParam()) {
        JsonArray result = new JsonArray();
        result.add(driver.getName());
        result.add(JsonParser.parseString(driver.getParam()));
        return result;
      }
      return new JsonPrimitive(driver.getName());
    }
  }

  /**
   * Serializer to convert {@link JobConfig.DecoratorList} to a string or a json array. It uses
   * {@link DriverSerializer} to handle each item in the decorator list.
   */
  private static class DecoratorListSerializer
      implements JsonSerializer<JobConfig.DecoratorList>,
          JsonDeserializer<JobConfig.DecoratorList> {

    @Override
    public JobConfig.DecoratorList deserialize(
        JsonElement json, Type typeOfT, JsonDeserializationContext context) {
      JobConfig.DecoratorList.Builder list = JobConfig.DecoratorList.newBuilder();
      for (JsonElement item : json.getAsJsonArray()) {
        list.addContent((JobConfig.Driver) context.deserialize(item, JobConfig.Driver.class));
      }
      return list.build();
    }

    @Override
    public JsonElement serialize(
        JobConfig.DecoratorList decoratorList, Type typeOfSrc, JsonSerializationContext context) {
      return context.serialize(decoratorList.getContentList());
    }
  }

  private JobConfigGsonHolder() {}
}
