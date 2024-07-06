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

package com.google.devtools.mobileharness.shared.util.message;

import static java.util.Comparator.comparing;

import com.google.auto.value.AutoValue;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.Type;
import com.google.protobuf.Message;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The proto util. */
public final class ProtoUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private ProtoUtil() {}

  /**
   * Converts a message to a different message whose fields are similar to the original one. If the
   * field's name is same and type is same, the fields is copied directly; If the field's name is
   * same, type is message and message type is not same, message is recursively copied; For the
   * left-over field which has no matched field in the source message, it's left in un-set state.
   *
   * @param source the source message
   * @param target the target message
   */
  public static void convert(Message source, Message.Builder target) {
    convert(source, target, ImmutableList.of());
  }

  /**
   * Converts a message to a different message whose fields are similar to the original one. If the
   * field's name is same and type is same, the fields is copied directly; If the field's name is
   * same, type is message and message type is not same, message is recursively copied; For the
   * left-over field which has no matched field in the source message, it's left in un-set state.
   *
   * @param source the source message
   * @param target the target message
   * @param ignoredFields the fields ignored to copy. It only works for the top level message
   */
  @SuppressWarnings("unchecked")
  public static void convert(Message source, Message.Builder target, List<String> ignoredFields) {
    Map<FieldDescriptor, Object> sourceFields = source.getAllFields();
    Map<String, FieldDescriptorAndField> sourceFieldsByName = new HashMap<>();
    sourceFields.forEach(
        (key, value) -> {
          if (!ignoredFields.contains(key.getName())) {
            sourceFieldsByName.put(key.getName(), FieldDescriptorAndField.of(key, value));
          }
        });
    for (FieldDescriptor targetField : target.getDescriptorForType().getFields()) {
      String targetFieldName = targetField.getName();
      if (sourceFieldsByName.containsKey(targetFieldName)) {
        FieldDescriptorAndField sourceField = sourceFieldsByName.get(targetFieldName);
        if (sourceField.fieldDescriptor().getType() == targetField.getType()
            && sourceField.fieldDescriptor().isRepeated() == targetField.isRepeated()) {
          if (sourceField.fieldDescriptor().getType() == Type.MESSAGE) {
            if (sourceField
                .fieldDescriptor()
                .getMessageType()
                .getFullName()
                .equals(targetField.getMessageType().getFullName())) {
              target.setField(targetField, sourceField.field());
            } else {
              if (sourceField.fieldDescriptor().isRepeated()) {
                List<Message> sourceSubMessages = (List<Message>) sourceField.field();
                for (Message sourceSubMessage : sourceSubMessages) {
                  Message.Builder targetSubMessage = target.newBuilderForField(targetField);
                  convert(sourceSubMessage, targetSubMessage);
                  target.addRepeatedField(targetField, targetSubMessage.buildPartial());
                }
              } else {
                Message.Builder targetSubMessage = target.newBuilderForField(targetField);
                convert((Message) sourceField.field(), targetSubMessage);
                target.setField(targetField, targetSubMessage.buildPartial());
              }
            }
          } else if (sourceField.fieldDescriptor().getType() == Type.ENUM) {
            if (sourceField
                .fieldDescriptor()
                .getEnumType()
                .getFullName()
                .equals(targetField.getEnumType().getFullName())) {
              target.setField(targetField, sourceField.field());
            } else {
              if (sourceField.fieldDescriptor().isRepeated()) {
                ((List<EnumValueDescriptor>) sourceField.field())
                    .forEach(
                        enumValueDescriptor -> {
                          EnumValueDescriptor targetEnumValue =
                              targetField
                                  .getEnumType()
                                  .findValueByName(enumValueDescriptor.getName());
                          if (targetEnumValue != null) {
                            target.addRepeatedField(targetField, targetEnumValue);
                          }
                        });

              } else {
                EnumValueDescriptor targetEnumValue =
                    targetField
                        .getEnumType()
                        .findValueByName(((EnumValueDescriptor) sourceField.field()).getName());
                if (targetEnumValue != null) {
                  target.setField(targetField, targetEnumValue);
                }
              }
            }
          } else if (sourceField.fieldDescriptor().getType()
              != Type.GROUP) { // Don't process group type.
            target.setField(targetField, sourceField.field());
          }
        }
      }
    }
  }

  /**
   * Gets the value from a proto according to the field path.
   *
   * <p>For example, if there is a proto message:
   *
   * <pre>
   * message MyProto {
   *   message SubMessage {
   *     string string_value = 1;
   *   }
   *   optional SubMessage sub_message = 1;
   * }
   * </pre>
   *
   * The field path for string_value in this proto is {"sub_message", "string_value"}. Given a
   * MyProto message and {"sub_message", "string_value"} as field path, this method will return the
   * value of string_value in it.
   *
   * @param message the message which contains the value
   * @param fieldPath the field name path of the value in the message, can't contain repeated/map
   *     field along it
   * @param clazz the class of the returned value
   * @return an Optional contains the value or empty if the path does not exist, or the type of the
   *     value is not match, or there is repeated/map field along the field path
   */
  public static <T> Optional<T> getValue(Message message, String[] fieldPath, Class<T> clazz) {
    if (fieldPath.length == 0) {
      return Optional.empty();
    }
    Message currentMessage = message;
    for (String field : Arrays.copyOf(fieldPath, fieldPath.length - 1)) {
      FieldDescriptor fieldDescriptor =
          currentMessage.getDescriptorForType().findFieldByName(field);
      if (fieldDescriptor == null
          || fieldDescriptor.getType() != FieldDescriptor.Type.MESSAGE
          || fieldDescriptor.isRepeated()
          || fieldDescriptor.isMapField()) {
        return Optional.empty();
      }
      currentMessage = (Message) message.getAllFields().get(fieldDescriptor);
      if (currentMessage == null) {
        return Optional.empty();
      }
    }
    FieldDescriptor fieldDescriptor =
        currentMessage.getDescriptorForType().findFieldByName(fieldPath[fieldPath.length - 1]);
    if (fieldDescriptor == null) {
      return Optional.empty();
    }
    Object value = currentMessage.getAllFields().get(fieldDescriptor);
    try {
      return Optional.ofNullable(clazz.cast(value));
    } catch (ClassCastException e) {
      return Optional.empty();
    }
  }

  /**
   * Sets the value of one field in a proto's Builder according to the field path.
   *
   * <p>Only support setting the field of type DOUBLE, INT64, INT32, BOOL, STRING.
   *
   * <p>For example, if there is a proto message:
   *
   * <pre>
   * message MyProto {
   *   message SubMessage {
   *     string string_value = 1;
   *     int32 int_value = 2;
   *   }
   *   optional SubMessage sub_message = 1;
   *   repeated SubMessage repeated_sub_message = 2;
   * }
   * </pre>
   *
   * <p>Call setValue(myProtoBuilder, {"sub_message", "int_value"}, "123") will set the int_value in
   * sub_message of myProtoBuilder to be 123.
   *
   * <p>Call setValue(myProtoBuilder, {"repeated_sub_message", "int_value"}, "123") will set the
   * int_value in first item of repeated_sub_message of myProtoBuilder to be 123.
   *
   * @param message the message builder
   * @param fieldPath the field name path of the field in the message
   * @param value the value need to be set
   * @throws MobileHarnessException if there is NumberFormatException or the field does not exist or
   *     its type is not supported
   */
  public static void setValue(Message.Builder message, String[] fieldPath, String value)
      throws MobileHarnessException {
    Map<String, FieldDescriptor> fields = getFields(message);
    FieldDescriptor descriptor = fields.get(fieldPath[0]);
    if (fieldPath.length == 1) {
      if (descriptor == null) {
        throw new MobileHarnessException(
            BasicErrorId.PROTO_FIELD_NOT_IN_MESSAGE,
            String.format("Field %s does not in message %s", fieldPath[0], message.getClass()));
      }
      try {
        switch (descriptor.getType()) {
          case DOUBLE:
            setValue(message, descriptor, Double.parseDouble(value));
            break;
          case INT64:
            setValue(message, descriptor, Long.parseLong(value));
            break;
          case INT32:
            setValue(message, descriptor, Integer.parseInt(value));
            break;
          case BOOL:
            setValue(message, descriptor, Boolean.parseBoolean(value));
            break;
          case STRING:
            setValue(message, descriptor, value);
            break;
          default:
            throw new MobileHarnessException(
                BasicErrorId.PROTO_MESSAGE_FIELD_TYPE_UNSUPPORTED,
                "Unsupported type while setting message value.");
        }

      } catch (NumberFormatException e) {
        throw new MobileHarnessException(
            BasicErrorId.PROTO_FIELD_VALUE_NUMBER_FORMAT_ERROR, "Failed to set value", e);
      }
    } else {
      String[] newFieldPath = Arrays.copyOfRange(fieldPath, 1, fieldPath.length);
      if (descriptor.isRepeated()) {
        if (message.getRepeatedFieldCount(descriptor) == 0) {
          message.addRepeatedField(descriptor, message.newBuilderForField(descriptor).build());
        }
        setValue(message.getRepeatedFieldBuilder(descriptor, 0), newFieldPath, value);
      } else {
        setValue(message.getFieldBuilder(descriptor), newFieldPath, value);
      }
    }
  }

  private static void setValue(Message.Builder message, FieldDescriptor descriptor, Object value) {
    if (descriptor.isRepeated()) {
      if (message.getRepeatedFieldCount(descriptor) == 0) {
        message.addRepeatedField(descriptor, message.newBuilderForField(descriptor).build());
      }
      message.setRepeatedField(descriptor, 0, value);
    } else {
      message.setField(descriptor, value);
    }
  }

  /** Gets the field name to FieldDescriptor map of a message. */
  public static Map<String, FieldDescriptor> getFields(Message.Builder message) {
    return message.getDescriptorForType().getFields().stream()
        .collect(Collectors.toMap(FieldDescriptor::getName, Function.identity()));
  }

  /**
   * Override the one message with the fields in the second same-type message. The difference from
   * Builder.mergeFrom is that the duplicated repeated fields are removed. And the overriding
   * message's repeated fields precede that overridden message.
   *
   * @param overridden the message to be overridden
   * @param overriding the message to override other message
   * @return the overridden message if two message's type are same; If not same, return the first
   *     message.
   */
  @CanIgnoreReturnValue
  public static <T extends Message.Builder> T overrideMessage(T overridden, T overriding) {
    if (!overridden
        .getDescriptorForType()
        .getFullName()
        .equals(overriding.getDescriptorForType().getFullName())) {
      return overridden;
    }
    Map<FieldDescriptor, Object> overridingFields = overriding.getAllFields();
    for (Entry<FieldDescriptor, Object> entry : overridingFields.entrySet()) {
      if (entry.getKey().isRepeated()) {
        List<Object> values = new ArrayList<>();

        int count = overriding.getRepeatedFieldCount(entry.getKey());
        for (int i = 0; i < count; i++) {
          values.add(overriding.getRepeatedField(entry.getKey(), i));
        }
        count = overridden.getRepeatedFieldCount(entry.getKey());
        for (int i = 0; i < count; i++) {
          values.add(overridden.getRepeatedField(entry.getKey(), i));
        }
        overridden.clearField(entry.getKey());
        values.stream()
            .distinct()
            .forEach(item -> overridden.addRepeatedField(entry.getKey(), item));

      } else {
        if (entry.getKey().getType() == Type.MESSAGE) {
          overridden.setField(
              entry.getKey(),
              overrideMessage(
                      overridden.getFieldBuilder(entry.getKey()),
                      overriding.getFieldBuilder(entry.getKey()))
                  .buildPartial());
        } else {
          overridden.setField(entry.getKey(), entry.getValue());
        }
      }
    }
    return overridden;
  }

  /**
   * Filters the fields in a protobuf message.
   *
   * <p>The selected fields should be kept and the ignored fields should be removed. If one field is
   * not in selected or ignored fields, it should use its nearest ancestor's selected/ignored rule.
   *
   * <p>If only set selectedFields, the root rule is ignored; Else, the root rule is selected.
   *
   * <p>Note that all map fields and their sub fields can not be filtered out.
   *
   * @param message the target message
   * @param selectedFields the fields to be kept
   * @param ignoredFields the fields to be removed
   * @param <T> concrete protobuf message builder.
   */
  public static <T extends Message.Builder> void filterMessageFields(
      T message, List<String> selectedFields, List<String> ignoredFields) {
    Stopwatch watch = Stopwatch.createStarted();
    Set<FieldNameAndShouldSelect> fieldFilterRuleSet = new HashSet<>();
    selectedFields.forEach(
        field -> fieldFilterRuleSet.add(FieldNameAndShouldSelect.of(field, true)));
    ignoredFields.forEach(
        field -> {
          if (!fieldFilterRuleSet.contains(FieldNameAndShouldSelect.of(field, true))) {
            fieldFilterRuleSet.add(FieldNameAndShouldSelect.of(field, false));
          }
        });
    List<FieldNameAndShouldSelect> filterFilterRuleList =
        fieldFilterRuleSet.stream()
            .sorted(
                comparing(FieldNameAndShouldSelect::fieldName)
                    .thenComparing(FieldNameAndShouldSelect::shouldSelect))
            .collect(Collectors.toList());
    if (filterFilterRuleList.isEmpty()) {
      return;
    }

    filterFilterRuleList.add(
        0, FieldNameAndShouldSelect.of("", !filterFilterRuleList.get(0).shouldSelect()));
    innerFilterFields(message, filterFilterRuleList, 0, 1, "");
    logger.atInfo().log("Filter message fields time: %s", watch.stop().elapsed());
  }

  /**
   * The recursive method to implement {@link #filterMessageFields} method.
   *
   * @param messageBuilder the target message builder.
   * @param fieldFilterRules the rules of field filter
   * @param thisMessageMatchedFilterRuleIndex the index in the field filter rules which should be
   *     used for this message. It should be this field or its nearest ancestor's rule.
   * @param iteratedFilterRuleIndex the iterated filter rule index
   * @param fieldFullName the full name of this message. The first level is the sub fields of given
   *     message in method {@link #filterMessageFields}.
   * @return a pair whose first value is the next iterated filter rule index and second value is
   *     whether this field should be removed from its parent.
   */
  @CanIgnoreReturnValue
  private static NextRuleIndexAndShouldRemove innerFilterFields(
      Message.Builder messageBuilder,
      List<FieldNameAndShouldSelect> fieldFilterRules,
      int thisMessageMatchedFilterRuleIndex,
      int iteratedFilterRuleIndex,
      String fieldFullName) {
    FieldNameAndShouldSelect thisMessageMatchedFilterRule =
        fieldFilterRules.get(thisMessageMatchedFilterRuleIndex);
    // Whether this message should be removed from its parent message.
    // One message cannot remove itself from its parent, so return this value to tell its parent to
    // do the remove work.
    boolean needRemove;
    // The prefix of the full name of the sub fields.
    String fieldFullNamePrefix = fieldFullName.isEmpty() ? "" : fieldFullName + ".";

    if (iteratedFilterRuleIndex == fieldFilterRules.size()
        || !fieldFilterRules
            .get(iteratedFilterRuleIndex)
            .fieldName()
            .startsWith(fieldFullNamePrefix)) {
      // If no rule matches the sub field of this message, use the current message's rule to process
      // this message as a whole.
      needRemove = !thisMessageMatchedFilterRule.shouldSelect();
    } else {
      needRemove = true;
      for (FieldDescriptor childFieldDescriptor :
          messageBuilder.getDescriptorForType().getFields().stream()
              .sorted(comparing(FieldDescriptor::getName))
              .collect(Collectors.toList())) {
        boolean fieldRemoved = false;
        String childFieldFullName = fieldFullNamePrefix + childFieldDescriptor.getName();
        // Skip the rule which is before this field of alphabet order.
        // Here both fields and rules are sorted, so we can safely skip these useless rules.
        while (iteratedFilterRuleIndex < fieldFilterRules.size()
            && fieldFilterRules
                    .get(iteratedFilterRuleIndex)
                    .fieldName()
                    .compareTo(childFieldFullName)
                < 0) {
          iteratedFilterRuleIndex++;
        }
        if (childFieldDescriptor.getType() != Type.MESSAGE) {
          // If it's a primitive value, no need to consider child fields.
          if (iteratedFilterRuleIndex < fieldFilterRules.size()
              && fieldFilterRules
                  .get(iteratedFilterRuleIndex)
                  .fieldName()
                  .equals(childFieldFullName)) {
            // If the iterated rule matches the current field
            if (!fieldFilterRules.get(iteratedFilterRuleIndex).shouldSelect()) {
              messageBuilder.clearField(childFieldDescriptor);
              fieldRemoved = true;
            }
            iteratedFilterRuleIndex++;
          } else {
            // If not match the iterated rule, uses parent(current message)'s rule.
            if (!thisMessageMatchedFilterRule.shouldSelect()) {
              messageBuilder.clearField(childFieldDescriptor);
              fieldRemoved = true;
            }
          }
        } else {
          // If it's a message, need to consider whether there's a rule matching the child fields.
          int childMessageMatchFilterRuleIndex = thisMessageMatchedFilterRuleIndex;
          int nextIteratedFilterRuleIndex = iteratedFilterRuleIndex;
          if (iteratedFilterRuleIndex < fieldFilterRules.size()
              && fieldFilterRules
                  .get(iteratedFilterRuleIndex)
                  .fieldName()
                  .equals(childFieldFullName)) {
            // If the iterated filter matches this sub field, uses the iterated rule as the rule for
            // this field.
            childMessageMatchFilterRuleIndex = iteratedFilterRuleIndex;
            nextIteratedFilterRuleIndex = iteratedFilterRuleIndex + 1;
          }

          if (childFieldDescriptor.isRepeated()) {
            if (!childFieldDescriptor.isMapField()) {
              int repeatedCount = messageBuilder.getRepeatedFieldCount(childFieldDescriptor);
              int lastIteratedFilterIndexInRepeatedField = iteratedFilterRuleIndex;

              // For repeated fields, need to iterate all item in the list.
              for (int i = 0; i < repeatedCount; i++) {
                Message.Builder childMessageBuilder =
                    messageBuilder.getRepeatedFieldBuilder(childFieldDescriptor, i);
                // Recursively call this method to process child message.
                NextRuleIndexAndShouldRemove result =
                    innerFilterFields(
                        childMessageBuilder,
                        fieldFilterRules,
                        childMessageMatchFilterRuleIndex,
                        nextIteratedFilterRuleIndex,
                        childFieldFullName);
                lastIteratedFilterIndexInRepeatedField = result.nextRuleIndex();
                if (result.shouldRemove()) {
                  // If one item should be removed, means this repeated field's all items should be
                  // removed. no need to continue for other items.
                  messageBuilder.clearField(childFieldDescriptor);
                  fieldRemoved = true;
                  break;
                }
              }
              iteratedFilterRuleIndex = lastIteratedFilterIndexInRepeatedField;
            }
          } else {
            // No need to check this sub message if its parent doesn't contain this field.
            if (!messageBuilder.hasField(childFieldDescriptor)) {
              fieldRemoved = true;
            } else {
              Message.Builder subMessageBuilder =
                  messageBuilder.getFieldBuilder(childFieldDescriptor);
              // Recursively call this method to process child message.
              NextRuleIndexAndShouldRemove result =
                  innerFilterFields(
                      subMessageBuilder,
                      fieldFilterRules,
                      childMessageMatchFilterRuleIndex,
                      nextIteratedFilterRuleIndex,
                      childFieldFullName);
              iteratedFilterRuleIndex = result.nextRuleIndex();
              if (result.shouldRemove()) {
                // Removes the child field if its rule need to remove.
                messageBuilder.clearField(childFieldDescriptor);
                fieldRemoved = true;
              }
            }
          }
        }
        // Only remove the message from its parent when all the child fields are removed.
        if (!fieldRemoved) {
          needRemove = false;
        }
      }
    }

    return NextRuleIndexAndShouldRemove.of(iteratedFilterRuleIndex, needRemove);
  }

  @AutoValue
  abstract static class FieldDescriptorAndField {
    abstract FieldDescriptor fieldDescriptor();

    abstract Object field();

    private static FieldDescriptorAndField of(FieldDescriptor fieldDescriptor, Object field) {
      return new AutoValue_ProtoUtil_FieldDescriptorAndField(fieldDescriptor, field);
    }
  }

  @AutoValue
  abstract static class FieldNameAndShouldSelect {
    abstract String fieldName();

    abstract boolean shouldSelect();

    private static FieldNameAndShouldSelect of(String fieldName, boolean shouldSelect) {
      return new AutoValue_ProtoUtil_FieldNameAndShouldSelect(fieldName, shouldSelect);
    }
  }

  @AutoValue
  abstract static class NextRuleIndexAndShouldRemove {

    /** The next iterated filter rule index. */
    abstract int nextRuleIndex();

    /** Whether this field should be removed from its parent. */
    abstract boolean shouldRemove();

    private static NextRuleIndexAndShouldRemove of(int nextRuleIndex, boolean shouldRemove) {
      return new AutoValue_ProtoUtil_NextRuleIndexAndShouldRemove(nextRuleIndex, shouldRemove);
    }
  }
}
