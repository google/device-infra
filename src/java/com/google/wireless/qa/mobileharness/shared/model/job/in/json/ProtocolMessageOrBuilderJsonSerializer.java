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

package com.google.wireless.qa.mobileharness.shared.model.job.in.json;

import static com.google.common.io.BaseEncoding.base64;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.ProtocolMessageEnum;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * General serializer for {@link MessageOrBuilder}s (Java proto2 API). This is meant to be
 * registered as a Hierarchical Type Adapter with the {@link com.google.gson.GsonBuilder} so that a
 * type adapter is not required to be registered for each protocol message used by the system.
 *
 * <p>It will also take a builder class and serialize it out as if the protocol message was built
 * and sent through Gson. Note, however, that it will only deserialize into a Protocol Message and
 * not its corresponding builder.
 *
 * <p>Below is a snippet showing how this class is expected to be registered with Gson:
 *
 * <pre class="code">
 *   Gson gson = new GsonBuilder()
 *       .registerTypeHierarchyAdapter(MessageOrBuilder.class,
 *           new ProtocolMessageOrBuilderJsonSerializer())
 *       .create();
 * </pre>
 *
 * <p><b>Note:</b> this class completely ignores proto2 extensions when deserializing Json to
 * proto2. It will serialize proto2 extensions to Json correctly, however.
 */
public class ProtocolMessageOrBuilderJsonSerializer
    implements JsonSerializer<MessageOrBuilder>, JsonDeserializer<MessageOrBuilder> {

  private final boolean allowUnknownFields;
  private final boolean partialBuild;
  private final boolean base64EncodedByteStrings;
  private final boolean accountForUnsignedTypes;

  // Function to transform proto field descriptors to json key names.
  private Function<FieldDescriptor, String> fieldDescriptorTransformer;

  /** Use {@link ProtocolMessageOrBuilderJsonSerializer#defaultSerializer} */
  @Deprecated
  public ProtocolMessageOrBuilderJsonSerializer() {
    this(false);
  }

  /** Use {@link ProtocolMessageOrBuilderJsonSerializer#newBuilder} */
  @Deprecated
  public ProtocolMessageOrBuilderJsonSerializer(boolean rejectUnknownFieldNames) {
    this(rejectUnknownFieldNames, Function.<String>identity(), false);
  }

  /** Use {@link ProtocolMessageOrBuilderJsonSerializer#newBuilder} */
  @Deprecated
  public ProtocolMessageOrBuilderJsonSerializer(
      boolean rejectUnknownFieldNames, boolean partialBuild) {
    this(rejectUnknownFieldNames, Function.<String>identity(), partialBuild);
  }

  /** Use {@link ProtocolMessageOrBuilderJsonSerializer#newBuilder} */
  @Deprecated
  public ProtocolMessageOrBuilderJsonSerializer(
      boolean rejectUnknownFieldNames, final Function<String, String> fieldNameConverter) {
    this(rejectUnknownFieldNames, fieldNameConverter, false);
  }

  /** Use {@link ProtocolMessageOrBuilderJsonSerializer#newBuilder} */
  @Deprecated
  public ProtocolMessageOrBuilderJsonSerializer(
      boolean rejectUnknownFieldNames, final Converter<String, String> fieldNameConverter) {
    // For compatibility with Java 7 users extending from com.google.common.base.Function
    this(rejectUnknownFieldNames, fieldNameConverter::convert, false);
  }

  /** Use {@link ProtocolMessageOrBuilderJsonSerializer#newBuilder} */
  @Deprecated
  public ProtocolMessageOrBuilderJsonSerializer(
      boolean rejectUnknownFieldNames,
      final Function<String, String> fieldNameConverter,
      boolean partialBuild) {
    this(
        newBuilder()
            .setAllowUnknownFields(!rejectUnknownFieldNames)
            .setPartialBuild(partialBuild)
            .setBase64EncodedByteStrings(true)
            .setFieldNameTransformer(fieldNameConverter));
  }

  /**
   * Creates an instance using the provided {@code ProcotolMessageOrBuilderJsonSerializer.Builder}
   */
  private ProtocolMessageOrBuilderJsonSerializer(Builder builder) {
    this.allowUnknownFields = builder.allowUnknownFields;
    this.partialBuild = builder.partialBuild;
    this.base64EncodedByteStrings = builder.base64EncodedByteStrings;
    this.fieldDescriptorTransformer = builder.fieldDescriptorTransformer;
    this.accountForUnsignedTypes = builder.accountForUnsignedTypes;
  }

  public static ProtocolMessageOrBuilderJsonSerializer defaultSerializer() {
    return newBuilder().build();
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  /** Builder for {@link ProtocolMessageOrBuilderJsonSerializer} */
  public static class Builder {
    private boolean allowUnknownFields = false;
    private boolean partialBuild = false;
    private boolean base64EncodedByteStrings = false;
    private boolean accountForUnsignedTypes = false;
    private Function<FieldDescriptor, String> fieldDescriptorTransformer =
        (FieldDescriptor field) ->
            CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, field.getName());

    private Builder() {}

    /**
     * If enabled, {@link JsonParseException} will not be throw during deserialization if the JSON
     * object contains a field that does not correspond to any message field.
     */
    @CanIgnoreReturnValue
    public Builder setAllowUnknownFields(boolean value) {
      this.allowUnknownFields = value;
      return this;
    }

    /**
     * If enabled, {@link Message.Builder#buildPartial} will be used during deserialization while
     * building the proto, thus skipping verification for required fields.
     */
    @CanIgnoreReturnValue
    public Builder setPartialBuild(boolean value) {
      this.partialBuild = value;
      return this;
    }

    /**
     * If enabled, any message fields with type {@code bytes} will be serialized using base64
     * encoding in the json representation. Json deserialization will use base64 decoding.
     * Otherwise, a serializer for {@link ByteString} will need to be provided in the Gson context.
     */
    @CanIgnoreReturnValue
    public Builder setBase64EncodedByteStrings(boolean value) {
      this.base64EncodedByteStrings = value;
      return this;
    }

    /**
     * Set a function for transforming proto field names to json key names. The default converts
     * lower underscore proto field names to lower camel case json key names. Either one of this or
     * {@link #setFieldDescriptorTransformer} should be used.
     */
    @CanIgnoreReturnValue
    public Builder setFieldNameTransformer(Function<String, String> value) {
      Preconditions.checkNotNull(value);
      this.fieldDescriptorTransformer = (FieldDescriptor field) -> value.apply(field.getName());
      return this;
    }

    /**
     * Set a function for transforming proto field descriptors to json key names. The default
     * converts lower underscore proto field names to lower camel case json key name. Either one of
     * this or {@link #setFieldNameTransformer} should be used.
     */
    @CanIgnoreReturnValue
    public Builder setFieldDescriptorTransformer(Function<FieldDescriptor, String> value) {
      this.fieldDescriptorTransformer = Preconditions.checkNotNull(value);
      return this;
    }

    /**
     * If enabled, any message fields with type {@code uint32}, {@code fixed32}, {@code uint64}, or
     * {@code fixed64} will be parsed as unsigned integers. {@code uint32} and {@code fixed32}
     * values will be serialized as long integers in JSON. {@code uint64} and {@code fixed64} values
     * will be serialized as strings in JSON (irrespective of the {@link LongSerializationPolicy}).
     */
    @CanIgnoreReturnValue
    public Builder setAccountForUnsignedTypes(boolean value) {
      this.accountForUnsignedTypes = value;
      return this;
    }

    public ProtocolMessageOrBuilderJsonSerializer build() {
      return new ProtocolMessageOrBuilderJsonSerializer(this);
    }
  }

  @Override
  public JsonElement serialize(
      MessageOrBuilder src, Type typeOfSrc, JsonSerializationContext context) {
    JsonObject ret = new JsonObject();
    Map<Descriptors.FieldDescriptor, Object> fields = src.getAllFields();

    for (Map.Entry<Descriptors.FieldDescriptor, Object> fieldPair : fields.entrySet()) {
      Descriptors.FieldDescriptor desc = fieldPair.getKey();

      // Handle a repeated field.
      if (desc.isRepeated()) {
        List<?> fieldList = (List<?>) fieldPair.getValue();
        if (!fieldList.isEmpty()) {
          JsonArray array = new JsonArray();
          for (Object o : fieldList) {
            array.add(serializeValue(o, desc, context));
          }
          ret.add(getFieldName(desc), array);
        }

        // Not a repeated field
      } else {
        ret.add(getFieldName(desc), serializeValue(fieldPair.getValue(), desc, context));
      }
    }
    return ret;
  }

  /**
   * Serializes a value into a JsonElement.
   *
   * @param value the value to serialize
   * @param field the field descriptor of the value to serialize
   * @param context the serialization context
   * @return the value serialized into a JsonElement
   */
  private JsonElement serializeValue(
      Object value, Descriptors.FieldDescriptor field, JsonSerializationContext context) {
    switch (field.getType()) {
      case ENUM:
        return serializeEnum((EnumValueDescriptor) value, field.getEnumType(), context);
      case BYTES:
        if (base64EncodedByteStrings) {
          return serializeByteString((ByteString) value);
        }
        return context.serialize(value);
      case UINT32:
      case FIXED32:
        if (accountForUnsignedTypes) {
          return new JsonPrimitive(Integer.toUnsignedLong((int) value));
        }
        return context.serialize(value);
      case UINT64:
      case FIXED64:
        if (accountForUnsignedTypes) {
          return new JsonPrimitive(Long.toUnsignedString((long) value));
        }
        return context.serialize(value);
      default:
        return context.serialize(value);
    }
  }

  private String getFieldName(FieldDescriptor fd) {
    return fieldDescriptorTransformer.apply(fd);
  }

  @CanIgnoreReturnValue
  @Deprecated
  public ProtocolMessageOrBuilderJsonSerializer setFieldNameConverter(
      Function<FieldDescriptor, String> fieldNameConverter) {
    this.fieldDescriptorTransformer =
        Preconditions.checkNotNull(fieldNameConverter, "fieldNameConverter");
    return this;
  }

  /**
   * Serializes a ProtocolMessageEnum.
   *
   * @param rawValue the field value to serialize
   * @param enumDesc the enum proto descriptor
   * @param context the actual context that performs the JSON serialization
   * @return the enum serialized into a JsonElement
   */
  private JsonElement serializeEnum(
      EnumValueDescriptor rawValue, EnumDescriptor enumDesc, JsonSerializationContext context) {
    final Class<? extends ProtocolMessageEnum> enumClass;
    try {
      enumClass = getProtoEnumClass(enumDesc);
    } catch (ClassNotFoundException e) {
      // No java class could be found for this enum. This can happen when the descriptor is
      // based on a non-compiled descriptor (e.g. DynamicMessage). Directly serialize as a string.
      return context.serialize(rawValue.getName());
    }

    final ProtocolMessageEnum enumValue;
    try {
      Method valueOfMethod =
          enumClass.getDeclaredMethod("valueOf", Descriptors.EnumValueDescriptor.class);
      enumValue = enumClass.cast(valueOfMethod.invoke(null, rawValue));
    } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
      // If we failed, fall back to serializing as a string
      return context.serialize(rawValue.getName());
    }

    return context.serialize(enumValue, enumClass);
  }

  /** Special handling for serializing ByteString as Base64 encoded strings. */
  private JsonElement serializeByteString(ByteString value) {
    return new JsonPrimitive(base64().encode(value.toByteArray()));
  }

  @Override
  public MessageOrBuilder deserialize(
      JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    if (!json.isJsonObject()) {
      throw new JsonParseException("Argument is not an object: " + json);
    }
    JsonObject obj = json.getAsJsonObject();

    Class<?> concreteClass = TypeToken.get(typeOfT).getRawType();
    Message.Builder resultBuilder = getBuilderForType(concreteClass);

    Descriptor descriptorForType = resultBuilder.getDescriptorForType();

    Set<String> knownFieldNames = new HashSet<>();
    for (Descriptors.FieldDescriptor desc : descriptorForType.getFields()) {
      String fieldName = getFieldName(desc);
      knownFieldNames.add(fieldName);
      if (obj.has(fieldName)) {
        try {
          JsonElement prop = obj.get(fieldName);
          if (prop.isJsonNull()) {
            // skip nulls entirely; the closest equivalent in proto is field absence.
          } else if (desc.isRepeated()) {
            if (!prop.isJsonArray()) {
              // if the field is not an array, add it as a single item into the repeated field.
              resultBuilder.addRepeatedField(desc, deserializeElement(prop, desc, context));
            } else {
              for (JsonElement arrayElement : prop.getAsJsonArray()) {
                if (arrayElement.isJsonNull()) {
                  onDeserializeNullInArray(resultBuilder, desc, prop.getAsJsonArray());
                }
                resultBuilder.addRepeatedField(
                    desc, deserializeElement(arrayElement, desc, context));
              }
            }
          } else {
            resultBuilder.setField(desc, deserializeElement(prop, desc, context));
          }
        } catch (ClassCastException e) {
          throw new JsonParseException(
              "Error Instantiating "
                  + concreteClass.getName()
                  + " during deserialization of proto field "
                  + desc.getFullName(),
              e);
        }
      } else if (desc.isRequired()) {
        onDeserializeRequiredFieldMissing(resultBuilder, desc, json);
      }
    }
    if (!resultBuilder.isInitialized()) {
      onDeserializeBuilderNotInitialized(resultBuilder, concreteClass);
    }
    if (!allowUnknownFields) {
      Set<String> foundFieldNames = new HashSet<>();
      for (Map.Entry<String, JsonElement> objField : obj.entrySet()) {
        foundFieldNames.add(objField.getKey());
      }
      Set<String> unknownFieldNames = Sets.difference(foundFieldNames, knownFieldNames);
      if (!unknownFieldNames.isEmpty()) {
        throw new JsonParseException("Unknown fields " + unknownFieldNames + ": " + json);
      }
    }

    return finalizeDeserialization(resultBuilder);
  }

  protected void onDeserializeNullInArray(
      Message.Builder resultBuilder, Descriptors.FieldDescriptor desc, JsonArray array) {
    throw new JsonParseException("null values in JSON arrays are not allowed");
  }

  protected void onDeserializeRequiredFieldMissing(
      Message.Builder resultBuilder, Descriptors.FieldDescriptor desc, JsonElement json) {
    if (!partialBuild) {
      String fieldName = getFieldName(desc);
      throw new JsonParseException("Required Field: " + fieldName + " is missing. " + json);
    }
  }

  protected void onDeserializeBuilderNotInitialized(
      Message.Builder resultBuilder, Class<?> concreteClass) {
    if (!partialBuild) {
      throw new JsonParseException(
          "Error Instantiating " + concreteClass.getName() + ": Initialization checks failed.");
    }
  }

  protected MessageOrBuilder finalizeDeserialization(Message.Builder builder) {
    if (partialBuild) {
      return builder.buildPartial();
    } else {
      return builder.build();
    }
  }

  /**
   * Deserializes a JsonElement.
   *
   * @param json the JsonElement to deserialize
   * @param field FieldDescriptor for the field providing the element to deserialize
   * @param context the deserialization context
   * @return the JsonElement deserialized into an Object
   */
  private Object deserializeElement(
      JsonElement json, FieldDescriptor field, JsonDeserializationContext context) {
    switch (field.getType()) {
      case BYTES:
        if (base64EncodedByteStrings) {
          return deserializeByteString(json);
        }
        return context.deserialize(json, ByteString.class);
      case ENUM:
        return deserializeEnum(json, field.getEnumType(), context);
      case GROUP:
      case MESSAGE:
        return deserializeMessage(json, field.getMessageType(), context);
      case BOOL:
        return context.deserialize(json, Boolean.class);
      case DOUBLE:
        return context.deserialize(json, Double.class);
      case FLOAT:
        return context.deserialize(json, Float.class);
      case INT32:
      case SINT32:
      case SFIXED32:
        return context.deserialize(json, Integer.class);
      case FIXED32:
      case UINT32:
        if (accountForUnsignedTypes) {
          return Integer.parseUnsignedInt(Long.toString(json.getAsLong()));
        }
        return context.deserialize(json, Integer.class);
      case INT64:
      case SINT64:
      case SFIXED64:
        return context.deserialize(json, Long.class);
      case FIXED64:
      case UINT64:
        if (accountForUnsignedTypes) {
          return Long.parseUnsignedLong(json.getAsString());
        }
        return context.deserialize(json, Long.class);
      case STRING:
        return context.deserialize(json, String.class);
      default:
        throw new IllegalStateException("Unhandled JavaType: " + field.getJavaType());
    }
  }

  /** Deserialize a ByteString JsonElement encoded as a Base64 string. */
  private ByteString deserializeByteString(JsonElement json) {
    try {
      return ByteString.copyFrom(base64().decode(json.getAsString()));
    } catch (IllegalArgumentException e) {
      throw new JsonParseException("Invalid Base64 bytes data: " + json);
    }
  }

  /** Deserialize a proto enum JsonElement. */
  private EnumValueDescriptor deserializeEnum(
      JsonElement json, EnumDescriptor enumDesc, JsonDeserializationContext context) {
    Class<? extends ProtocolMessageEnum> enumClass;
    try {
      enumClass = getProtoEnumClass(enumDesc);
    } catch (ClassNotFoundException e) {
      throw new JsonParseException(
          "Cannot instantiate enum class for field " + enumDesc.getName(), e);
    }

    ProtocolMessageEnum deserializedEnum =
        (ProtocolMessageEnum) context.deserialize(json, enumClass);
    if (deserializedEnum == null) {
      throw new JsonParseException(
          String.format(
              "Error deserializing enum type [%s]. " + "Unrecognized value [%s]",
              enumDesc.getName(), json.getAsString()));
    }

    return deserializedEnum.getValueDescriptor();
  }

  /** Deserialize a proto message JsonElement. */
  private MessageOrBuilder deserializeMessage(
      JsonElement json, Descriptor messageDesc, JsonDeserializationContext context) {
    Class<? extends MessageOrBuilder> messageClass;
    try {
      messageClass = getMessageClass(messageDesc);
    } catch (ClassNotFoundException e) {
      throw new JsonParseException("Message class not found for " + messageDesc.getName(), e);
    }

    return messageClass.cast(context.deserialize(json, messageClass));
  }

  /**
   * Gets a builder for the concrete class using reflection.
   *
   * @param concreteClass the actual "Message" class that will be built
   */
  private Message.Builder getBuilderForType(Class<?> concreteClass) throws JsonParseException {
    Method newBuilderMethod;
    try {
      newBuilderMethod = concreteClass.getDeclaredMethod("newBuilder");
    } catch (NoSuchMethodException e) {
      throw new JsonParseException("Error Instantiating " + concreteClass.getName(), e);
    } catch (SecurityException e) {
      throw new JsonParseException("Error Instantiating " + concreteClass.getName(), e);
    }
    if (!Modifier.isStatic(newBuilderMethod.getModifiers())) {
      throw new JsonParseException(
          "Error Instantiating "
              + concreteClass.getName()
              + " because the \"newBuilder\" method is not static on the class.");
    }

    try {
      return (Message.Builder) newBuilderMethod.invoke(null);
    } catch (IllegalAccessException e) {
      throw new JsonParseException("Error Instantiating " + concreteClass.getName(), e);
    } catch (InvocationTargetException e) {
      throw new JsonParseException("Error Instantiating " + concreteClass.getName(), e);
    }
  }

  /**
   * Gets the java package and perhaps containing class with trailing dot or dollar.
   *
   * @param fileDesc descriptor for the file containing the Message or Enum
   */
  private String getJavaPrefix(Descriptors.FileDescriptor fileDesc) {
    StringBuilder result = new StringBuilder();
    DescriptorProtos.FileOptions options = fileDesc.getOptions();

    String javaPackage;
    if (options.hasJavaPackage()) {
      javaPackage = options.getJavaPackage();
    } else {
      String protoPackage = fileDesc.getPackage();
      javaPackage = "com.google.protos" + ("".equals(protoPackage) ? "" : "." + protoPackage);
    }
    result.append(javaPackage).append(".");

    if (!options.getJavaMultipleFiles()) {
      String className = classForFile(fileDesc);
      result.append(className).append("$");
    }

    return result.toString();
  }

  /**
   * Gets a java class from a FieldDescriptor for an enum.
   *
   * @param enumDesc EnumDescriptor to get the java class from
   */
  private Class<? extends ProtocolMessageEnum> getProtoEnumClass(
      Descriptors.EnumDescriptor enumDesc) throws ClassNotFoundException {
    StringBuilder javaType = new StringBuilder(enumDesc.getName());
    Descriptors.Descriptor parent = enumDesc.getContainingType();

    while (parent != null) {
      javaType.insert(0, "$").insert(0, parent.getName());
      parent = parent.getContainingType();
    }

    Descriptors.FileDescriptor fileDesc = enumDesc.getFile();
    javaType.insert(0, getJavaPrefix(fileDesc));

    return Class.forName(javaType.toString()).asSubclass(ProtocolMessageEnum.class);
  }

  /**
   * Gets a java class from a FieldDescriptor for a message.
   *
   * @param messageDesc Descriptor to get the java class from
   */
  private Class<? extends MessageOrBuilder> getMessageClass(Descriptors.Descriptor messageDesc)
      throws ClassNotFoundException {
    StringBuilder javaType = new StringBuilder(messageDesc.getName());
    Descriptors.Descriptor parent = messageDesc.getContainingType();

    while (parent != null) {
      javaType.insert(0, "$").insert(0, parent.getName());
      parent = parent.getContainingType();
    }

    Descriptors.FileDescriptor fileDesc = messageDesc.getFile();
    javaType.insert(0, getJavaPrefix(fileDesc));

    return Class.forName(javaType.toString()).asSubclass(MessageOrBuilder.class);
  }

  /**
   * Builds a java class name from a FileDescriptor. This algorithm is hand copied from the protobuf
   * c++ code that compiles proto files.
   *
   * @param fileDesc the FileDescriptor from which to build a class name
   */
  private String classForFile(Descriptors.FileDescriptor fileDesc) {
    DescriptorProtos.FileOptions options = fileDesc.getOptions();
    if (options.hasJavaOuterClassname()) {
      return options.getJavaOuterClassname();
    } else {
      // The general algorithm is:
      // UnderscoresToCamel(StripExtension(BaseName(file)))
      StringBuilder className = new StringBuilder();
      String fileName = fileDesc.getName();
      int startPoint = fileName.lastIndexOf('/') + 1;
      int lastPoint = fileName.length();
      if (fileName.endsWith(".proto")) {
        lastPoint -= ".proto".length();
      } else if (fileName.endsWith(".protodevel")) {
        lastPoint -= ".protodevel".length();
      }
      String plainName = fileName.substring(startPoint, lastPoint);
      boolean capitalizeNext = true;
      for (int i = 0; i < plainName.length(); i++) {
        char c = plainName.charAt(i);
        if ('a' <= c && c <= 'z') {
          if (capitalizeNext) {
            className.append(Character.toChars(c + ('A' - 'a'))[0]);
          } else {
            className.append(c);
          }
          capitalizeNext = false;
        } else if ('A' <= c && c <= 'Z') {
          className.append(c);
          capitalizeNext = false;
        } else if ('0' <= c && c <= '9') {
          className.append(c);
          capitalizeNext = true;
        } else {
          capitalizeNext = true;
        }
      }
      String classNameStr = className.toString();

      // When the file contains a message with the same name, then "OuterClass" is appended to the
      // file class by default.
      for (Descriptor messageDesc : fileDesc.getMessageTypes()) {
        if (classNameStr.equals(messageDesc.getName())) {
          return classNameStr + "OuterClass";
        }
      }

      // The same applies to enums.
      for (EnumDescriptor enumDesc : fileDesc.getEnumTypes()) {
        if (classNameStr.equals(enumDesc.getName())) {
          return classNameStr + "OuterClass";
        }
      }

      return classNameStr;
    }
  }
}
