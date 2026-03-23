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
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfoMonitor;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedTestModuleResultsMonitor;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder.Default;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.InvocationHandlerAdapter;

/**
 * An agent to record Tradefed runtime info (invocations) and test module results (module and test
 * execution progress).
 */
public class TradefedInvocationAgent {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * The entry point.
   *
   * @param agentArgs [String runtimeInfoFilePath]:[String testModuleResultsFilePath]
   */
  public static void premain(String agentArgs, Instrumentation inst) {
    new TradefedInvocationAgent().run(agentArgs, inst);
  }

  @SuppressWarnings("StringSplitter")
  private void run(String agentArgs, Instrumentation inst) {
    // Parses arguments.
    String[] arguments = agentArgs.split(":");
    String runtimeInfoFilePath = arguments.length >= 1 ? arguments[0] : "";
    String testModuleResultsFilePath = arguments.length >= 2 ? arguments[1] : "";

    // Initializes logger.
    TradefedInvocationAgentLogger.init();

    if (runtimeInfoFilePath.isEmpty() || testModuleResultsFilePath.isEmpty()) {
      logger.atSevere().log(
          "Require both runtime info file path and test module results file path. agentArgs=%s",
          agentArgs);
      return;
    }
    logger.atInfo().log("Runtime info file path: %s", runtimeInfoFilePath);
    logger.atInfo().log("Test module results file path: %s", testModuleResultsFilePath);

    XtsTradefedRuntimeInfoMonitor.getInstance().start(Path.of(runtimeInfoFilePath));
    XtsTradefedTestModuleResultsMonitor.getInstance().start(Path.of(testModuleResultsFilePath));

    intercept(
        inst,
        "com.android.tradefed.invoker.TestInvocation",
        "invoke",
        4,
        TradefedTestInvocationInvokeMethodInterceptor.class);
    intercept(
        inst,
        "com.android.tradefed.cluster.ClusterCommandScheduler$InvocationEventHandler",
        "invocationComplete",
        2,
        TradefedInvocationEventHandlerInvocationCompleteMethodInterceptor.class);
    intercept(
        inst,
        "com.android.tradefed.result.CollectingTestListener",
        "invocationFailed",
        1,
        TradefedCollectingTestListenerInvocationFailedMethodInterceptor.class);
  }

  private static void intercept(
      Instrumentation inst,
      String className,
      String methodName,
      int argumentsLength,
      Class<?> interceptorClass) {
    intercept(
        inst,
        className,
        (builder, typeDescription, classLoader, module, protectionDomain) ->
            builder.visit(
                Advice.to(interceptorClass)
                    .on(named(methodName).and(takesArguments(argumentsLength)))));
  }

  private static void intercept(Instrumentation inst, String className, Transformer transformer) {
    new Default()
        .with(toSystemError().withErrorsOnly())
        .disableClassFormatChanges()
        .ignore(none(), not(isBootstrapClassLoader()))
        .type(named(className))
        .transform(transformer)
        .installOn(inst);
  }

  private static class TradefedTestInvocationInvokeMethodInterceptor {

    @Advice.OnMethodEnter()
    public static void onEnter(
        @Advice.This Object testInvocation,
        @Advice.Argument(value = 0) Object invocationContext,
        @Advice.Argument(value = 1) Object configuration) {
      XtsTradefedRuntimeInfoMonitor.getInstance()
          .onInvocationEnter(testInvocation, invocationContext);

      // Implement an extra listener (Tradefed `ITestInvocationListener`) purely at runtime using
      // reflection, and inject it to monitor test module execution start and end events.
      try {
        ClassLoader loader = configuration.getClass().getClassLoader();
        Class<?> listenerInterface =
            loader.loadClass("com.android.tradefed.result.ITestInvocationListener");
        Object testModuleListener =
            new ByteBuddy()
                .subclass(Object.class)
                .implement(listenerInterface)
                .method(any())
                .intercept(InvocationHandlerAdapter.of(new TestModuleListenerInvocationHandler()))
                .make()
                .load(loader)
                .getLoaded()
                .getDeclaredConstructor()
                .newInstance();

        // Inject the custom test module listener:
        @SuppressWarnings("unchecked") // safe by specification
        List<Object> listeners =
            (List<Object>)
                configuration
                    .getClass()
                    .getMethod("getTestInvocationListeners")
                    .invoke(configuration);
        listeners.add(testModuleListener);
      } catch (Exception e) {
        logger.atWarning().withCause(e).log(
            "Failed to inject the custom test module invocation listener");
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(
        @Advice.This Object testInvocation,
        @Advice.Argument(value = 0) Object invocationContext,
        @Advice.Thrown Throwable exception) {
      XtsTradefedRuntimeInfoMonitor.getInstance()
          .onInvocationExit(testInvocation, invocationContext, exception);
    }
  }

  private static class TradefedInvocationEventHandlerInvocationCompleteMethodInterceptor {

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(
        @Advice.This Object invocationEventHandler,
        @Advice.Argument(value = 0) Object invocationContext,
        @Advice.Thrown Throwable exception) {
      XtsTradefedRuntimeInfoMonitor.getInstance()
          .onInvocationComplete(invocationEventHandler, invocationContext, exception);
    }
  }

  private static class TradefedCollectingTestListenerInvocationFailedMethodInterceptor {
    @Advice.OnMethodEnter()
    public static void onEnter(@Advice.Argument(value = 0) Object exception) {
      XtsTradefedRuntimeInfoMonitor.getInstance().onInvocationFailed((Throwable) exception);
    }
  }

  /** Invocation handler for intercepting Tradefed test and module execution events. */
  public static class TestModuleListenerInvocationHandler implements InvocationHandler {
    private String invocationId;

    @Override
    @SuppressWarnings("ReturnMissingNullable") // To avoid pulling in the Guava dep (for
    // the Nullable annotation) to keep the size of the agent jar small.
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (method.getDeclaringClass() == Object.class) {
        return method.invoke(this, args);
      }

      switch (method.getName()) {
        case "testModuleStarted" -> {
          Object moduleContext = args[0];
          invocationId = XtsTradefedTestModuleResultsMonitor.getInvocationId(moduleContext);
          XtsTradefedTestModuleResultsMonitor.getInstance()
              .onModuleStart(moduleContext, invocationId);
        }
        case "testModuleEnded" -> {
          if (invocationId != null) {
            XtsTradefedTestModuleResultsMonitor.getInstance().onModuleEnd(invocationId);
          }
        }
        case "testRunStarted" -> {
          // `testRunStarted` accepts `String runName` and `int testCount`.
          if (invocationId != null && args.length >= 2 && args[1] instanceof Integer) {
            XtsTradefedTestModuleResultsMonitor.getInstance()
                .onTestRunStarted(
                    invocationId,
                    /* runName= */ (String) args[0],
                    /* testCount= */ (Integer) args[1]);
          }
        }
        case "testRunEnded" -> {
          // `testRunEnded` accepts `long elapsedTime` and `HashMap<String, Metric> runMetrics`.
          if (invocationId != null && args.length >= 1 && args[0] instanceof Long) {
            XtsTradefedTestModuleResultsMonitor.getInstance()
                .onTestRunEnded(invocationId, /* elapsedTime= */ Duration.ofMillis((Long) args[0]));
          }
        }
        case "testStarted",
            "testEnded",
            "testFailed",
            "testAssumptionFailure",
            "testIgnored",
            "testSkipped" -> {
          // For these methods, args[0] is `TestDescription`
          XtsTradefedTestModuleResultsMonitor.getInstance()
              .onTestEvent(
                  invocationId,
                  /* eventType= */ method.getName(),
                  /* testId= */ args[0].toString());
        }
        default -> {}
      }
      // The `ITestInvocationListener` methods intercepted here all have a void return type, so we
      // return null to satisfy the `InvocationHandler` interface.
      return null;
    }
  }

  private TradefedInvocationAgent() {}
}
