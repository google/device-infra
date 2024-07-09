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

package com.google.devtools.mobileharness.platform.android.xts.agent;

import static net.bytebuddy.agent.builder.AgentBuilder.Listener.StreamWriting.toSystemError;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.common.flogger.FluentLogger;
import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

/** The agent to intercept the {@code TestInvocation} class in Tradefed to get the runtime info. */
public class TradefedInvocationAgent {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static void premain(String agentArgs, Instrumentation inst) {
    new TradefedInvocationAgent().run(agentArgs, inst);
  }

  private void run(String configPath, Instrumentation inst) {
    TradefedInvocationAgentLogger.init();
    logger.atInfo().log("Agent config path: %s", configPath);
    interceptTradefedTestInvocation(inst);
  }

  private void interceptTradefedTestInvocation(Instrumentation inst) {
    new AgentBuilder.Default()
        .with(toSystemError().withErrorsOnly())
        .disableClassFormatChanges()
        .ignore(none(), not(isBootstrapClassLoader()))
        .type(named("com.android.tradefed.invoker.TestInvocation"))
        .transform(
            (builder, typeDescription, classLoader, module, protectionDomain) ->
                builder.visit(
                    Advice.to(TradefedTestInvocationInterceptor.class)
                        .on(named("invoke").and(takesArguments(4)))))
        .installOn(inst);
  }

  private static class TradefedTestInvocationInterceptor {
    @Advice.OnMethodEnter()
    public static void onEnter(
        // @Advice.This Object testInvocation, @Advice.Argument(value = 0) Object invocationContext
        ) {}

    @Advice.OnMethodExit()
    public static void onExit(
        // @Advice.This Object testInvocation, @Advice.Argument(value = 0) Object invocationContext
        ) {}
  }

  private TradefedInvocationAgent() {}
}
