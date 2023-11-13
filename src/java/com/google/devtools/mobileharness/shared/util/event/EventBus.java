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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.event.EventBusBackend.SubscriberMethod;
import com.google.devtools.mobileharness.shared.util.event.EventBusBackend.SubscriberMethodSearchResult;
import com.google.devtools.mobileharness.shared.util.event.proto.EventBusProto;
import com.google.devtools.mobileharness.shared.util.event.proto.EventBusProto.EventReceiving;
import com.google.devtools.mobileharness.shared.util.event.proto.EventBusProto.EventStatistics;
import com.google.devtools.mobileharness.shared.util.event.proto.EventBusProto.ObjectSummary;
import com.google.devtools.mobileharness.shared.util.event.proto.EventBusProto.TimingInfo;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * Similar to Guava {@linkplain com.google.common.eventbus.EventBus EventBus}, but supporting
 * features:
 *
 * <ol>
 *   <li>Subscriber objects receive an event in registration order or reversed.
 *   <li>Logs that a subscriber method starts/ends receiving an event.
 *   <li>Per-post exception handler instead of per-event-bus exception handler.
 *   <li>Returns statistics (e.g., timing info of each subscriber method) for each post.
 *   <li>Multi-event post in subscriber object order (e.g., 1st subscriber receives event A and B,
 *       and then 2nd subscriber receives event A and B).
 * </ol>
 *
 * See {@linkplain #post(List, SubscriberOrder, SubscriberExceptionHandler) post} for more details.
 */
public class EventBus {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Handler for handling an exception thrown from a subscriber method. */
  public interface SubscriberExceptionHandler {

    void handleException(Throwable exception, SubscriberExceptionContext context);
  }

  /** Context for {@link SubscriberExceptionHandler#handleException}. */
  @AutoValue
  public abstract static class SubscriberExceptionContext {

    public abstract Object event();

    public abstract Throwable exception();

    public abstract Object subscriberObject();

    public abstract Method subscriberMethod();

    public static SubscriberExceptionContext of(
        Object event, Throwable exception, Object subscriberObject, Method subscriberMethod) {
      return new AutoValue_EventBus_SubscriberExceptionContext(
          event, exception, subscriberObject, subscriberMethod);
    }
  }

  /** Order that subscribers receive an event. */
  public enum SubscriberOrder {
    /** Subscribers receive events in the order they registered. */
    REGISTER,

    /** Subscribers receive events in the reverse order of their registration. */
    REVERSED,
  }

  private static final EventBusBackend BACKEND = new EventBusBackend();

  @Nullable private final SubscriberExceptionHandler globalExceptionHandler;

  @GuardedBy("itself")
  private final List<SubscriberMethodSearchResult> subscribers = new ArrayList<>();

  /** Creates an event bus without event bus global exception handler. */
  public EventBus() {
    this(null);
  }

  /** Creates an event bus with the given event bus global exception handler. */
  public EventBus(@Nullable SubscriberExceptionHandler globalExceptionHandler) {
    this.globalExceptionHandler = globalExceptionHandler;
  }

  /**
   * Registers a subscriber object. See {@linkplain #post(List, SubscriberOrder,
   * SubscriberExceptionHandler) post} for more details.
   */
  public void register(Object subscriberObject) {
    SubscriberMethodSearchResult subscriber = BACKEND.searchSubscriberMethods(subscriberObject);

    // Logs invalid subscriber methods if any.
    if (!subscriber.invalidSubscriberMethods().isEmpty()) {
      logger.atWarning().log(
          "Invalid subscriber methods when adding subscriber object %s@%s: %s",
          subscriberObject.getClass().getName(),
          System.identityHashCode(subscriberObject),
          subscriber.invalidSubscriberMethods());
    }

    synchronized (subscribers) {
      subscribers.add(subscriber);
    }
  }

  /**
   * Equivalent to {@linkplain #post(List, SubscriberOrder, SubscriberExceptionHandler)
   * post(ImmutableList.of(event), REGISTER, null)}.
   */
  @CanIgnoreReturnValue
  public EventStatistics post(Object event) {
    return post(ImmutableList.of(event), SubscriberOrder.REGISTER, /* exceptionHandler= */ null);
  }

  /**
   * Posts one event (or more) to registered subscriber objects' subscriber methods that <b>can</b>
   * receive the event.
   *
   * <p><b>Subscriber method:</b> A subscriber method is a method annotated with {@link
   * com.google.common.eventbus.Subscribe @Subscribe}, declared in the class of a {@linkplain
   * #register registered} subscriber object, with exactly one (non-primitive type) parameter.
   *
   * <p><b>Receive event:</b> A subscriber method <b>can</b> receive an event if the parameter type
   * of the method is {@linkplain Class#isAssignableFrom class/superclass} of the event object. A
   * subscriber method receives an event means calling the subscriber method's {@link Method#invoke}
   * on the subscriber object of the subscriber method, with the event object as the parameter.
   *
   * <p><b>Synchronization:</b> Subscriber methods receive an event in <b>serial</b> when the event
   * is posted. After <b>all</b> subscriber methods receive the posted event and return, this method
   * will return.
   *
   * <p><b>Order:</b> Subscriber methods receive an event in the order their subscriber objects
   * registered (using {@link SubscriberOrder#REGISTER}) or reversed (using {@link
   * SubscriberOrder#REVERSED}). Note that subscriber methods of one subscriber object may
   * <b>not</b> be in their declaration order.
   *
   * <p><b>Multiple events in one post:</b> If there are multiple events in {@code events}, the
   * outer loop iterates over subscriber methods and the inner loop iterates over events. It is
   * useful when migrating an event type to another without breaking the original subscriber order.
   *
   * <p><b>Exception:</b> Exceptions thrown from subscriber methods will be caught and logged.
   * Additionally, if {@code exceptionHandler} and/or {@code globalExceptionHandler} are specified,
   * they will also be called ({@code globalExceptionHandler} will be called first). Exceptions
   * thrown from {@code exceptionHandler} and/or {@code globalExceptionHandler} will also be caught
   * and logged. If a subscriber method throws an {@link InterruptedException}, {@link
   * Thread#currentThread()}{@linkplain Thread#interrupt() .interrupt()} will be called, which may
   * cause the successor subscriber method to throw {@link InterruptedException}.
   *
   * <p><b>Logging:</b> The start and end that a subscriber method receives an event will be logged.
   *
   * <p><b>Statistic:</b> Execution time of each subscriber method is recorded in the returned
   * {@link EventStatistics}.
   *
   * <p><b>Thread safety:</b> Multiple {@link #register} and {@link #post} can be invoked at the
   * same time, but a subscriber method needs to ensure its own thread safety.
   */
  @CanIgnoreReturnValue
  public EventStatistics post(
      List<Object> events,
      SubscriberOrder order,
      @Nullable SubscriberExceptionHandler exceptionHandler) {
    EventStatistics.Builder statistics = EventStatistics.newBuilder();

    // Gets a snapshot of subscribers.
    ImmutableList<SubscriberMethodSearchResult> subscribers;
    synchronized (this.subscribers) {
      subscribers = ImmutableList.copyOf(this.subscribers);
    }

    // Finds all suitable subscriber methods.
    for (SubscriberMethodSearchResult subscriber :
        order.equals(SubscriberOrder.REGISTER) ? subscribers : subscribers.reverse()) {
      ImmutableList<SubscriberMethod> subscriberMethods = subscriber.subscriberMethods();
      for (SubscriberMethod subscriberMethod :
          order.equals(SubscriberOrder.REGISTER)
              ? subscriberMethods
              : subscriberMethods.reverse()) {
        for (Object event : events) {
          if (subscriberMethod.canReceiveEvent(event.getClass())) {

            // Posts the event to the subscriber method.
            logger.atInfo().log("Posting event [%s] to subscriber [%s]", event, subscriberMethod);
            Throwable exception = null;
            boolean interrupted = false;
            TimingInfo.Builder timingInfo =
                TimingInfo.newBuilder().setStartTime(TimeUtils.toProtoTimestamp(Instant.now()));
            try {
              subscriberMethod.receiveEvent(event);
            } catch (Throwable e) {
              exception = e;
              if (e instanceof InterruptedException) {
                interrupted = true;
              }
            }
            timingInfo.setEndTime(TimeUtils.toProtoTimestamp(Instant.now()));
            logger.atInfo().withCause(exception).log(
                "Event [%s] posted to subscriber [%s]", event, subscriberMethod);

            // Calls SubscriberExceptionHandler.
            if (exception != null && (exceptionHandler != null || globalExceptionHandler != null)) {
              SubscriberExceptionContext context =
                  SubscriberExceptionContext.of(
                      event, exception, subscriber.subscriberObject(), subscriberMethod.method());
              if (globalExceptionHandler != null) {
                try {
                  globalExceptionHandler.handleException(exception, context);
                } catch (RuntimeException | Error e) {
                  logger.atWarning().withCause(e).log(
                      "Error occurred when event bus global exception handler is handling"
                          + " subscriber exception, event=%s, subscriber=%s",
                      event, subscriberMethod);
                }
              }
              if (exceptionHandler != null) {
                try {
                  exceptionHandler.handleException(exception, context);
                } catch (RuntimeException | Error e) {
                  logger.atWarning().withCause(e).log(
                      "Error occurred when handling subscriber exception, event=%s, subscriber=%s",
                      event, subscriberMethod);
                }
              }
            }

            // Creates EventReceiving proto.
            EventReceiving.Builder eventReceiving =
                EventReceiving.newBuilder()
                    .setSubscriberObject(getObjectSummary(subscriber.subscriberObject()))
                    .setSubscriberMethod(
                        EventBusProto.SubscriberMethod.newBuilder()
                            .setMethodName(subscriberMethod.method().getName())
                            .setParameterClassName(subscriberMethod.parameter().getName()))
                    .setEvent(getObjectSummary(event))
                    .setTimingInfo(timingInfo);
            if (exception != null) {
              eventReceiving.setException(getObjectSummary(exception));
            }
            statistics.addEventReceiving(eventReceiving);

            if (interrupted) {
              Thread.currentThread().interrupt();
            }
          }
        }
      }
    }

    return statistics.build();
  }

  private static ObjectSummary getObjectSummary(Object object) {
    return ObjectSummary.newBuilder()
        .setClassName(object.getClass().getName())
        .setIdentityHashCode(System.identityHashCode(object))
        .build();
  }
}
