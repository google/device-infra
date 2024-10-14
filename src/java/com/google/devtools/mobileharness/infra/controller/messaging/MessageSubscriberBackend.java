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

package com.google.devtools.mobileharness.infra.controller.messaging;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.messaging.MessageEvent;
import com.google.devtools.mobileharness.api.messaging.SubscribeMessage;
import com.google.protobuf.Message;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.List;

/** Backend of message subscribers. */
public class MessageSubscriberBackend {

  /** Searches all message subscribers in the given object. */
  public static MessageSubscribers searchMessageSubscribers(Object object) {
    Class<?> clazz = object.getClass();
    ImmutableList.Builder<MessageSubscriber> messageSubscribers = ImmutableList.builder();
    ImmutableList.Builder<InvalidMessageSubscriber> invalidMessageSubscribers =
        ImmutableList.builder();

    for (Method method : clazz.getDeclaredMethods()) {
      // Filters methods without @SubscribeMessage annotation.
      if (!method.isAnnotationPresent(SubscribeMessage.class)) {
        continue;
      }

      // Filters synthetic methods.
      if (method.isSynthetic()) {
        // See https://github.com/google/guava/issues/1549
        continue;
      }

      // Filters methods with zero or multiple parameters.
      Parameter[] parameters = method.getParameters();
      if (parameters.length != 1) {
        invalidMessageSubscribers.add(
            InvalidMessageSubscriber.of(
                clazz,
                method,
                String.format(
                    "Message subscriber should have exactly one parameter but has [%s]",
                    parameters.length)));
        continue;
      }

      // Filters methods whose parameter type is not MessageEvent.
      Parameter parameter = parameters[0];
      if (!parameter.getType().equals(MessageEvent.class)) {
        invalidMessageSubscribers.add(
            InvalidMessageSubscriber.of(
                clazz,
                method,
                String.format(
                    "Message subscriber parameter type should be MessageEvent but is [%s]",
                    parameter.getType())));
        continue;
      }

      // Filters methods whose parameter type is not parameterized.
      Type parameterType = parameter.getParameterizedType();
      if (!(parameterType instanceof ParameterizedType)) {
        invalidMessageSubscribers.add(
            InvalidMessageSubscriber.of(
                clazz, method, "Message subscriber parameter type should be parameterized"));
        continue;
      }

      // Filters methods whose parameter generic type is a type variable or a wildcard type.
      Type typeArgument = ((ParameterizedType) parameterType).getActualTypeArguments()[0];
      if (typeArgument instanceof TypeVariable || typeArgument instanceof WildcardType) {
        invalidMessageSubscribers.add(
            InvalidMessageSubscriber.of(
                clazz,
                method,
                String.format(
                    "Message subscriber parameter generic type should not be a type variable or a"
                        + " wildcard type but is [%s]",
                    typeArgument)));
        continue;
      }

      // The type argument will never be ParameterizedType or GenericArrayType, so it will always be
      // Class<?>. Furthermore, it will always be Class<? extends Message>.
      @SuppressWarnings("unchecked")
      Class<? extends Message> messageType = (Class<? extends Message>) typeArgument;

      // Filters methods whose return value type is not a Protobuf message.
      Class<?> returnType = method.getReturnType();
      if (!Message.class.isAssignableFrom(returnType)) {
        invalidMessageSubscribers.add(
            InvalidMessageSubscriber.of(
                clazz,
                method,
                String.format(
                    "Message subscriber result type should be a Protobuf message but is [%s]",
                    returnType)));
        continue;
      }
      @SuppressWarnings("unchecked")
      Class<? extends Message> resultType = (Class<? extends Message>) returnType;

      // Makes the method accessible.
      method.setAccessible(true);

      messageSubscribers.add(MessageSubscriber.of(object, clazz, method, messageType, resultType));
    }

    return MessageSubscribers.of(
        object, messageSubscribers.build(), invalidMessageSubscribers.build());
  }

  /** A message subscriber. */
  @AutoValue
  public abstract static class MessageSubscriber {

    public abstract Object obj();

    public abstract Class<?> clazz();

    /**
     * {@linkplain Method#setAccessible(boolean) setAccessible(true)} has been invoked on the
     * method.
     */
    public abstract Method method();

    public abstract Class<? extends Message> messageType();

    public abstract Class<? extends Message> resultType();

    @Memoized
    @Override
    public String toString() {
      return String.format(
          "%s#%s(MessageEvent<%s>):%s@%s",
          clazz().getName(),
          method().getName(),
          messageType().getSimpleName(),
          resultType().getSimpleName(),
          System.identityHashCode(obj()));
    }

    public static MessageSubscriber of(
        Object obj,
        Class<?> clazz,
        Method method,
        Class<? extends Message> messageType,
        Class<? extends Message> resultType) {
      return new AutoValue_MessageSubscriberBackend_MessageSubscriber(
          obj, clazz, method, messageType, resultType);
    }
  }

  /** An invalid message subscriber. */
  @AutoValue
  public abstract static class InvalidMessageSubscriber {

    public abstract Class<?> clazz();

    public abstract Method method();

    public abstract String reason();

    public static InvalidMessageSubscriber of(Class<?> clazz, Method method, String reason) {
      return new AutoValue_MessageSubscriberBackend_InvalidMessageSubscriber(clazz, method, reason);
    }
  }

  /** Message subscribers of an object. */
  @AutoValue
  public abstract static class MessageSubscribers {

    public abstract Object obj();

    public abstract ImmutableList<MessageSubscriber> messageSubscribers();

    public abstract ImmutableList<InvalidMessageSubscriber> invalidMessageSubscribers();

    public static MessageSubscribers of(
        Object obj,
        List<MessageSubscriber> messageSubscribers,
        List<InvalidMessageSubscriber> invalidMessageSubscribers) {
      return new AutoValue_MessageSubscriberBackend_MessageSubscribers(
          obj,
          ImmutableList.copyOf(messageSubscribers),
          ImmutableList.copyOf(invalidMessageSubscribers));
    }
  }

  private MessageSubscriberBackend() {}
}
