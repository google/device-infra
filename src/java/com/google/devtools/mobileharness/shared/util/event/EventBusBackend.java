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

package com.google.devtools.mobileharness.shared.util.event;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Arrays.stream;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/** Backend of MH {@code EventBus} library which enhances Guava {@link EventBus} library. */
public class EventBusBackend {

  /**
   * A subscriber method of a class.
   *
   * <p>A subscriber method of a class is a declared method of the class, which is annotated with
   * {@link Subscribe} and has one and only one non-primitive type parameter.
   */
  @AutoValue
  public abstract static class SubscriberMethod {

    public abstract Object subscriberObject();

    public abstract Class<?> clazz();

    /**
     * Returns the subscriber method on which {@link Method#setAccessible(boolean)} has been invoked
     * with {@code true}.
     */
    public abstract Method method();

    public abstract Class<?> parameter();

    public boolean canReceiveEvent(Class<?> eventClass) {
      return parameter().isAssignableFrom(eventClass);
    }

    /**
     * Invokes the subscriber method, with the given event.
     *
     * @throws Throwable the original exception thrown from the subscriber method
     */
    public void receiveEvent(Object event) throws Throwable {
      checkArgument(canReceiveEvent(event.getClass()));
      try {
        method().invoke(subscriberObject(), event);
      } catch (IllegalAccessException e) {
        throw new AssertionError(e); // The method has been set to accessible.
      } catch (InvocationTargetException e) {
        throw e.getTargetException();
      }
    }

    @Memoized
    @Override
    public String toString() {
      return String.format(
          "%s#%s(%s)@%s",
          clazz().getName(),
          method().getName(),
          parameter().getSimpleName(),
          System.identityHashCode(subscriberObject()));
    }

    public static SubscriberMethod of(
        Object subscriberObject, Class<?> clazz, Method method, Class<?> parameter) {
      return new AutoValue_EventBusBackend_SubscriberMethod(
          subscriberObject, clazz, method, parameter);
    }
  }

  /**
   * An invalid subscriber method, e.g., which is annotated with {@link Subscribe} but whose
   * declaration does not meet the requirement of a subscriber method.
   */
  @AutoValue
  public abstract static class InvalidSubscriberMethod {

    public abstract Class<?> clazz();

    public abstract Method method();

    /** Returns the reason why the method is an invalid subscriber method. */
    public abstract String reason();

    public static InvalidSubscriberMethod of(Class<?> clazz, Method method, String reason) {
      return new AutoValue_EventBusBackend_InvalidSubscriberMethod(clazz, method, reason);
    }
  }

  /** Result of searching subscriber methods of a given class. */
  @AutoValue
  public abstract static class SubscriberMethodSearchResult {

    public abstract Object subscriberObject();

    public abstract ImmutableList<SubscriberMethod> subscriberMethods();

    public abstract ImmutableList<InvalidSubscriberMethod> invalidSubscriberMethods();

    public static SubscriberMethodSearchResult of(
        Object subscriberObject,
        List<SubscriberMethod> subscriberMethods,
        List<InvalidSubscriberMethod> invalidSubscriberMethods) {
      return new AutoValue_EventBusBackend_SubscriberMethodSearchResult(
          subscriberObject,
          ImmutableList.copyOf(subscriberMethods),
          ImmutableList.copyOf(invalidSubscriberMethods));
    }
  }

  /** Searches {@link SubscriberMethod}s in a given object. */
  public SubscriberMethodSearchResult searchSubscriberMethods(Object subscriberObject) {
    Class<?> clazz = subscriberObject.getClass();
    ImmutableList.Builder<SubscriberMethod> subscriberMethods = ImmutableList.builder();
    ImmutableList.Builder<InvalidSubscriberMethod> invalidSubscriberMethods =
        ImmutableList.builder();

    // Traverses all declared methods of the class.
    stream(clazz.getDeclaredMethods())
        .forEach(
            method -> {
              // Filters methods without @Subscribe annotation.
              if (!method.isAnnotationPresent(Subscribe.class)) {
                return;
              }

              // Filters synthetic methods.
              if (method.isSynthetic()) {
                // See https://github.com/google/guava/issues/1549
                return;
              }

              // Filters methods with zero or multiple parameters.
              Class<?>[] parameters = method.getParameterTypes();
              if (parameters.length != 1) {
                invalidSubscriberMethods.add(
                    InvalidSubscriberMethod.of(
                        clazz, method, "Subscriber method should have exactly one parameter"));
                return;
              }

              // Filters methods with primitive type parameter.
              Class<?> parameter = parameters[0];
              if (parameter.isPrimitive()) {
                // See https://github.com/google/guava/issues/3992
                invalidSubscriberMethods.add(
                    InvalidSubscriberMethod.of(
                        clazz,
                        method,
                        "Subscriber method should have a non-primitive type parameter"));
                return;
              }

              // Makes the method accessible.
              method.setAccessible(true);

              subscriberMethods.add(
                  SubscriberMethod.of(subscriberObject, clazz, method, parameter));
            });
    return SubscriberMethodSearchResult.of(
        subscriberObject, subscriberMethods.build(), invalidSubscriberMethods.build());
  }
}
