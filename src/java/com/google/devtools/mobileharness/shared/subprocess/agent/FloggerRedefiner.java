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

package com.google.devtools.mobileharness.shared.subprocess.agent;

import static net.bytebuddy.matcher.ElementMatchers.named;

import java.util.logging.Level;
import java.util.logging.Logger;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.ClassFileLocator.ForClassLoader;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy.Default;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.pool.TypePool;

/** Utility for redefining flogger classes. */
public class FloggerRedefiner {

  private static final Logger logger = Logger.getLogger(FloggerRedefiner.class.getName());

  private static final String SIMPLE_MESSAGE_FORMATTER_CLASS_NAME =
      "com.google.common.flogger.backend.SimpleMessageFormatter";

  /**
   * Redefines {@linkplain com.google.common.flogger.backend.SimpleMessageFormatter#appendContext
   * SimpleMessageFormatter.appendContext()} with an empty implementation.
   */
  public static void redefineSimpleMessageFormatter() {
    try (Unloaded<?> type =
        new ByteBuddy()
            .redefine(
                TypePool.Default.ofSystemLoader()
                    .describe(SIMPLE_MESSAGE_FORMATTER_CLASS_NAME)
                    .resolve(),
                ForClassLoader.ofSystemLoader())
            .method(named("appendContext"))
            .intercept(MethodDelegation.to(EmptyMessageFormatter.class))
            .make()) {
      type.load(ClassLoader.getSystemClassLoader(), Default.INJECTION).getLoaded();
    } catch (RuntimeException | Error e) {
      logger.log(Level.WARNING, "Failed to redefine " + SIMPLE_MESSAGE_FORMATTER_CLASS_NAME, e);
    }
  }

  /** Byte Buddy requires this class to be public. */
  public static class EmptyMessageFormatter {

    @SuppressWarnings({"unused", "CanIgnoreReturnValueSuggester"})
    public static StringBuilder appendContext(
        Object logData, Object metadata, StringBuilder buffer) {
      return buffer;
    }

    private EmptyMessageFormatter() {}
  }

  private FloggerRedefiner() {}
}
