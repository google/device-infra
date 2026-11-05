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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.Type;
import com.google.protobuf.Message;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Specs.MessageGsonHolder;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecHelper;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecWrapper;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.spec.JobSpec;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/** Scoped specs. Users could store same tags with different values under different namespace. */
public class ScopedSpecs implements JobSpecWrapper {

  // TODO: Move all FILE TAGS to package {@code c.g.w.q.m.shared.comm.filetransfer}.
  /** Prefix of file tags in returns of method {@link #getFiles}. */
  public static final String FILE_TAG_PREFIX = "<<**++--$$SCOPED_SPECS$$--++**>>";

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Parameters belongs to each namespace. The key is namespace name, value is a json element
   * contains all necessary data.
   */
  private final Map<String, JsonObject> specs =
      Collections.synchronizedMap(new HashMap<String, JsonObject>());

  /** The time records. */
  @Nullable private final Timing timing;

  private Params globalParams;

  /** Creates a scoped specs. */
  public ScopedSpecs(@Nullable Timing timing) {
    this(new Params(timing), timing);
  }

  /** Creates a scoped specs with {@code globalParams} as the default value. */
  public ScopedSpecs(Params globalParams, @Nullable Timing timing) {
    this.globalParams = globalParams;
    this.timing = timing;
  }

  /**
   * Creates a scoped specs with {@code globalParams} and the string representation of a {@link
   * ScopedSpecs}. Note: please don't make this public at any time.
   */
  ScopedSpecs(Params globalParams, Timing timing, String scopedSpecsJsonString) {
    this.globalParams = globalParams;
    this.timing = timing;
    if (!scopedSpecsJsonString.isEmpty()) {
      try {
        JsonElement jsonElement = JsonParser.parseString(scopedSpecsJsonString);
        if (jsonElement.isJsonObject()) {
          for (Map.Entry<String, JsonElement> entry : jsonElement.getAsJsonObject().entrySet()) {
            this.specs.put(entry.getKey(), deepCopy(entry.getValue().getAsJsonObject()));
          }
        }
      } catch (JsonSyntaxException | IllegalStateException e) {
        logger.atWarning().withCause(e).log(
            "Fail to recover a ScopedSpecs by the given json string: %s", scopedSpecsJsonString);
      }
    }
  }

  /** Adds the json object {@code specs}. */
  @CanIgnoreReturnValue
  public ScopedSpecs add(String namespace, JsonObject specs) {
    this.specs.put(namespace, deepCopy(specs));
    touch();
    return this;
  }

  /** Adds the proto buffer {@code message}. */
  @CanIgnoreReturnValue
  public ScopedSpecs add(String namespace, Message message) throws MobileHarnessException {
    JsonObject json;
    try {
      json = MessageGsonHolder.gson.toJsonTree(message).getAsJsonObject();
    } catch (IllegalStateException e) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_SPEC_PARSE_JSON_ERROR,
          "Failed to convert message to an json object:" + specs,
          e);
    }
    this.specs.put(namespace, json);
    touch();
    return this;
  }

  /**
   * Adds every entry of {@code json} as a pair of <namespace, specs>. The value of any entry must
   * be a {@link JsonObject}, or an exception will be threw.
   */
  @CanIgnoreReturnValue
  public ScopedSpecs addJson(JsonObject json) throws MobileHarnessException {
    for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
      try {
        add(entry.getKey(), entry.getValue().getAsJsonObject());
      } catch (IllegalStateException e) {
        throw new MobileHarnessException(
            BasicErrorId.JOB_SPEC_PARSE_JSON_ERROR,
            String.format(
                "Invalid scoped specs pair in json: <%s, %s>", entry.getKey(), entry.getValue()),
            e);
      }
    }
    return this;
  }

  /**
   * Adds the json object converts from {@code jsonString}. The value of any entry of the converted
   * json object must be a {@link JsonObject} too, or an exception will be threw.
   */
  @CanIgnoreReturnValue
  public ScopedSpecs addJson(String jsonString) throws MobileHarnessException {
    if (Strings.isNullOrEmpty(jsonString)) {
      return this;
    }

    JsonElement json;
    try {
      json = JsonParser.parseString(jsonString);
    } catch (JsonSyntaxException e) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_SPEC_PARSE_JSON_ERROR,
          "Failed to parse string to a json object: " + jsonString,
          e);
    }
    if (!json.isJsonObject()) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_SPEC_PARSE_JSON_ERROR, "not a json object: " + jsonString);
    }
    return addJson(json.getAsJsonObject());
  }

  /** Adds every entry in {@code specs}. */
  @CanIgnoreReturnValue
  public ScopedSpecs addAll(Map<String, JsonObject> specs) {
    // No easy way to deep copy a JsonObject, we just serialize it to string then
    // deserialize it back.
    for (Map.Entry<String, JsonObject> entry : specs.entrySet()) {
      this.specs.put(entry.getKey(), deepCopy(entry.getValue()));
    }
    touch();
    return this;
  }

  /**
   * Adds every spec extensions in {@code jobSpec}. The namespace of each spec is the simple message
   * name.
   */
  @CanIgnoreReturnValue
  public ScopedSpecs addAll(JobSpec jobSpec) throws MobileHarnessException {
    addExtensionInMessage(jobSpec.getDecoratorSpec());
    addExtensionInMessage(jobSpec.getDriverSpec());
    return this;
  }

  /** Adds extensions of {@code message}. This method is a helper of {@link #addAll}. */
  private void addExtensionInMessage(Message message) throws MobileHarnessException {
    for (Map.Entry<FieldDescriptor, Object> entry : message.getAllFields().entrySet()) {
      FieldDescriptor field = entry.getKey();
      if (!field.isExtension() || field.getType() != Type.MESSAGE || field.isRepeated()) {
        continue;
      }
      add(field.getMessageType().getName(), (Message) entry.getValue());
    }
  }

  /** Copies data from {@code other}. All existing data will be wiped out before copying. */
  public ImmutableMap<String, JsonObject> getAll() {
    return ImmutableMap.copyOf(specs);
  }

  /**
   * Gets specs for {@code namespace}. If {@code mergeGlobalParams} is true, {@code Params.getXxx}
   * will merge value with global specs which has the same key. Returns an empty specs if failed to
   * find one.
   */
  public Specs get(String namespace, boolean mergeGlobalParams) {
    JsonObject specs = this.specs.get(namespace);
    if (specs == null) {
      specs = new JsonObject();
    }
    if (mergeGlobalParams) {
      return new Specs(specs, globalParams);
    }
    return new Specs(specs, new Params(timing));
  }

  /** Gets Specs for {@code namespace}. Returns an empty specs if failed to find one. */
  public Specs get(String namespace) {
    return get(namespace, false);
  }

  /**
   * Gets spec data of class {@code specClass} from wrapped data.
   *
   * @throws MobileHarnessException if {@code specClass} is not a valid extension of {@link JobSpec}
   */
  @Override
  public <T extends Message> T getSpec(Class<T> specClass) throws MobileHarnessException {
    return get(specClass.getSimpleName()).asMessage(specClass);
  }

  /**
   * Gets merged spec data of class {@code specClass} from wrapped data, which will merge value with
   * global specs which has the same key. Returns an empty specs if failed to find one.
   */
  public <T extends Message> T getMergedSpec(Class<T> specClass) throws MobileHarnessException {
    return get(specClass.getSimpleName(), true).asMessage(specClass);
  }

  /**
   * Gets all file paths in scoped spec. It converts scoped spec into a JobSpec protobuf at first,
   * then extract all files in the protobuf, because all *file* a defined in the JobSpec field.
   *
   * @param helper helper for converting scoped spec into a JobSpec protobuf
   * @return a map with entries in the form {@code <{@link #FILE_TAG_PREFIX} + path, path>}
   */
  public Map<String, String> getFiles(JobSpecHelper helper)
      throws MobileHarnessException, InterruptedException {
    Map<String, String> files = new HashMap<>();
    for (String file : JobSpecHelper.getFiles(toJobSpec(helper)).values()) {
      files.put(FILE_TAG_PREFIX + file, file);
    }
    return files;
  }

  /** Deep copy json object {@code object}. */
  private JsonObject deepCopy(JsonObject object) {
    return (JsonObject) JsonParser.parseString(object.toString());
  }

  /** Converts current scoped specs into a json object. */
  public JsonObject asJsonObject() {
    JsonObject object = new JsonObject();
    for (Map.Entry<String, JsonObject> entry : specs.entrySet()) {
      object.add(entry.getKey(), entry.getValue());
    }
    return object;
  }

  /** Gets the json string of current scoped specs. */
  public String toJsonString() {
    return GsonHolder.gson.toJson(asJsonObject());
  }

  /**
   * Converts current scoped specs to a {@link JobSpec}. Ignore any namespaces which is not a simple
   * class name({@link Class#getSimpleName()}) of registered specs in {@code helper}.
   */
  public JobSpec toJobSpec(JobSpecHelper helper) {
    JobSpec.Builder jobSpec = JobSpec.newBuilder();
    for (Class<? extends Message> specClass : helper.getRegisteredExtensionClasses()) {
      String className = specClass.getSimpleName();
      if (!specs.containsKey(className)) {
        continue;
      }
      Specs spec = get(className);
      try {
        Message message = spec.asMessage(specClass);
        helper.setSpec(jobSpec, message);
      } catch (MobileHarnessException e) {
        logger.atWarning().log(
            "Failed to add scoped spec to a JobSpec: %s\n%s", e.getMessage(), spec);
      }
    }
    return jobSpec.build();
  }

  /** Tries to touch {@link #timing} if it is available. */
  private void touch() {
    if (timing != null) {
      timing.touch();
    }
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof ScopedSpecs)) {
      return false;
    }
    return ((ScopedSpecs) other).asJsonObject().equals(asJsonObject());
  }

  @Override
  public int hashCode() {
    return asJsonObject().hashCode();
  }

  @Override
  public String toString() {
    return toJsonString();
  }

  /** Holder of a gson object. */
  private static class GsonHolder {
    private static final Gson gson =
        new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
  }
}
