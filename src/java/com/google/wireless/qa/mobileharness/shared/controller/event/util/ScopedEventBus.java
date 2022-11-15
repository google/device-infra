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

package com.google.wireless.qa.mobileharness.shared.controller.event.util;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;
import com.google.devtools.mobileharness.infra.controller.test.util.SubscriberExceptionLoggingHandler;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.wireless.qa.mobileharness.shared.constant.ExitCode;
import java.util.EnumMap;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Multiple buses to handle events in different scopes with different handlers.
 *
 * @param <S> scope enum type
 */
public class ScopedEventBus<S extends Enum<S>> {

  private final EnumMap<S, EventBus> buses;

  public ScopedEventBus(Class<S> scopeType) {
    buses = new EnumMap<>(scopeType);
  }

  /** Sets the given scope to a new event bus. */
  public void add(S scope) {
    add(scope, /* bus= */ null);
  }

  /** Sets the given event bus to the given scope. */
  public void add(S scope, @Nullable EventBus bus) {
    buses.put(scope, bus == null ? new EventBus(new SubscriberExceptionLoggingHandler()) : bus);
  }

  /**
   * Get the event bus in the given scope.
   *
   * @throws NullPointerException if no event bus for the given scope
   */
  public EventBus inScope(S scope) {
    return checkNotNull(
        buses.get(scope),
        "Scope %s.%s doesn't exist",
        scope.getDeclaringClass().getCanonicalName(),
        scope.name());
  }

  /** Posts the event to the given scopes. */
  @SafeVarargs
  public final void post(Object event, S... scopes) {
    post(ImmutableList.of(event), scopes);
  }

  /** Posts the events to the given scopes. */
  @SafeVarargs
  public final void post(List<Object> events, S... scopes) {
    try {
      for (S scope : scopes) {
        EventBus eventBus = inScope(scope);
        events.forEach(
            event -> {
              if (event instanceof InjectionEvent) {
                ((InjectionEvent) event).enter();
              }

              eventBus.post(event);

              if (event instanceof InjectionEvent) {
                ((InjectionEvent) event).leave();
              }
            });
      }
    } catch (NullPointerException e) {
      new SystemUtil().exit(ExitCode.Shared.CODING_ERROR, e);
    }
  }
}
