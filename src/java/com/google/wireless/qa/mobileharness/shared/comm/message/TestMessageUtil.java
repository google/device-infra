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

package com.google.wireless.qa.mobileharness.shared.comm.message;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.comm.messaging.message.TestMessageInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Utils for sending and subscribing test messages. */
public class TestMessageUtil {

  /**
   * The reserved namespace prefix for MH internal messages.
   *
   * <p>The namespace prefix could not be used in non-MH code.
   */
  public static final String MH_NAMESPACE_PREFIX = "mobileharness:";

  /** The prefix pattern of the MH package name. */
  private static final Pattern MH_PACKAGE_PATTERN =
      Pattern.compile("com\\.google\\.(wireless\\.qa|devtools)\\.mobileharness\\..+");

  /**
   * See {@link #sendMessageToTest(TestInfo, Map)}.
   *
   * <p>The difference is that this method can <b>only</b> send message to a <b>top-level</b> test,
   * which does not have a parent test. If you want to send message to a sub test, you should use
   * {@link #sendMessageToTest(TestInfo, Map)} instead.
   */
  public void sendMessageToTest(String rootTestId, Map<String, String> message)
      throws MobileHarnessException {
    sendMessageToTest(rootTestId, ImmutableList.of(rootTestId), message);
  }

  /**
   * Sends a message to a test. See go/mh-messaging.
   *
   * <p>The following tests can be accessed:
   *
   * <ul>
   *   <li>In MH client, all running tests in the same process where a MH client plugin runs (in
   *       detail, all running tests started by MH client API in this process);
   *   <li>In MH lab, all running tests in the same process where a MH driver/decorator/plugin (MH
   *       test runner) runs.
   * </ul>
   *
   * <p>The following modules will receive the message:
   *
   * <ul>
   *   <li>All client and lab plugins of the root test of the test. These plugins {@linkplain
   *       com.google.common.eventbus.Subscribe subscribes} {@linkplain
   *       com.google.wireless.qa.mobileharness.shared.comm.message.event.TestMessageEvent
   *       TestMessageEvent};
   *   <li>The driver and all decorators of the root test of the test.
   * </ul>
   *
   * <p>They can get the message by {@linkplain
   * com.google.wireless.qa.mobileharness.shared.comm.message.event.TestMessageEvent#getMessage
   * TestMessageEvent.getMessage()}.
   *
   * <p>Note that because all test message subscribers are registered by root test, even a message
   * is sent to a sub test, its root test will receive the message. Drivers can forward the message
   * to the sub test themselves if necessary.
   *
   * <p>Because all subscribers of a test will receive all messages of the test, it is recommended
   * to add a {@code namespace} item to the message to send. You can use your project name as the
   * value of the namespace. For example, add {@code <"namespace", "your_project_name">} to the
   * message to send and in your plugin, only handle messages whose namespace equals {@code
   * "your_project_name"}. Note that the namespace should not start with {@linkplain
   * #MH_NAMESPACE_PREFIX "mobileharness:"} or an exception will be thrown.
   *
   * <p>Messages will be forwarded automatically between MH client and lab if the test is in the
   * {@linkplain com.google.wireless.qa.mobileharness.client.api.mode.remote.RemoteMode remote
   * mode}.
   *
   * <p>Note that because the TEST network restriction, there will be some delay for the client side
   * to receive messages from the lab side.
   *
   * <p>This method is not thread safe.
   *
   * @param test a running MH test
   * @param message the message to send
   * @throws MobileHarnessException if fails to send the message
   * @see <a href="go/mh-messaging">Mobile Harness Messaging</a>
   * @see com.google.common.eventbus.EventBus
   * @see <a href="go/mhv4-plugin">Mobile Harness Plugin</a>
   */
  public void sendMessageToTest(TestInfo test, Map<String, String> message)
      throws MobileHarnessException {
    sendMessageToTest(test.getRootTest().locator().getId(), getSubTestIdChain(test), message);
  }

  private void sendMessageToTest(
      String rootTestId, List<String> subTestIdChain, Map<String, String> message)
      throws MobileHarnessException {
    checkNamespace(message == null ? null : message.get("namespace"));
    TestMessageManager.getInstance()
        .sendMessageToTest(
            TestMessageInfo.of(rootTestId, message, subTestIdChain, /* isRemote= */ false));
  }

  private ImmutableList<String> getSubTestIdChain(TestInfo testInfo) {
    List<TestInfo> parents = new ArrayList<>(1);
    for (TestInfo current = testInfo; current != null; current = current.parentTest()) {
      parents.add(current);
    }
    return Lists.reverse(parents).stream()
        .map(TestInfo::locator)
        .map(TestLocator::getId)
        .collect(toImmutableList());
  }

  /**
   * Checks whether the namespace of a message is valid.
   *
   * <p>If the invoker of {@link #sendMessageToTest} is non-MH code, the namespace should not start
   * with {@link #MH_NAMESPACE_PREFIX}.
   *
   * @param namespace the namespace of the message
   * @throws MobileHarnessException if the namespace is invalid
   */
  private void checkNamespace(String namespace) throws MobileHarnessException {
    if (namespace != null
        && namespace.startsWith(MH_NAMESPACE_PREFIX)
        && !MH_PACKAGE_PATTERN
            .matcher(new Throwable().getStackTrace()[2].getClassName())
            .matches()) {
      throw new MobileHarnessException(
          InfraErrorId.TR_SEND_TEST_MESSAGE_ILLEGAL_MESSAGE_NAMESPACE,
          String.format(
              "The namespace of the message should not start with \"%s\"", MH_NAMESPACE_PREFIX));
    }
  }
}
