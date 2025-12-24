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

package com.google.devtools.mobileharness.shared.util.system;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.google.devtools.mobileharness.shared.util.system.ShutdownHookManager.Priority;
import com.google.devtools.mobileharness.shared.util.system.ShutdownHookManager.Task;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class ShutdownHookManagerTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private Task task1;
  @Mock private Task task2;
  @Mock private Task task3;

  private final ShutdownHookManager shutdownHookManager =
      new ShutdownHookManager(/* enable= */ false);

  @Test
  public void addShutdownHook_inOrder() throws Exception {
    shutdownHookManager.addShutdownHook(task1, "task-1");
    shutdownHookManager.addShutdownHook(task2, "task-2");
    shutdownHookManager.addShutdownHook(task3, "task-3", Priority.DEFAULT.earlier(100));

    shutdownHookManager.shutdown();

    InOrder inOrder = inOrder(task1, task2, task3);
    inOrder.verify(task3).run();
    inOrder.verify(task1).run();
    inOrder.verify(task2).run();
  }

  @Test
  public void addShutdownHook_runOnce() throws Exception {
    shutdownHookManager.addShutdownHook(task1, "task-1");

    shutdownHookManager.shutdown();
    shutdownHookManager.shutdown();

    verify(task1).run();
  }

  @Test
  public void addShutdownHook_exception() throws Exception {
    doThrow(IOException.class).when(task1).run();

    shutdownHookManager.addShutdownHook(task1, "task-1");
    shutdownHookManager.addShutdownHook(task2, "task-2");

    shutdownHookManager.shutdown();

    verify(task2).run();
  }

  @Test
  public void addShutdownHook_interrupted() throws Exception {
    doThrow(InterruptedException.class).when(task1).run();

    AtomicBoolean task2Interrupted = new AtomicBoolean();
    doAnswer(
            invocation -> {
              task2Interrupted.set(Thread.currentThread().isInterrupted());
              return null;
            })
        .when(task2)
        .run();

    shutdownHookManager.addShutdownHook(task1, "task-1");
    shutdownHookManager.addShutdownHook(task2, "task-2");

    shutdownHookManager.shutdown();

    verify(task2).run();
    assertThat(task2Interrupted.get()).isTrue();
  }
}
