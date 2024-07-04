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

package com.google.devtools.mobileharness.infra.ats.common.plan;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import javax.inject.Inject;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** A parser to parse test plans from the xts-tradefed.jar file only. */
public class TestPlanParser {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String CONFIGURATION_NODE_NAME = "configuration";
  private static final String OPTION_NODE_NAME = "option";
  private static final String INCLUDE_NODE_NAME = "include";

  private static final String ATTR_NAME_KEY = "name";
  private static final String ATTR_VALUE_KEY = "value";

  private static final String INCLUDE_FILTER_ATTR_NAME = "compatibility:include-filter";
  private static final String EXCLUDE_FILTER_ATTR_NAME = "compatibility:exclude-filter";

  private final PlanConfigUtil planConfigUtil;

  @Inject
  TestPlanParser(PlanConfigUtil planConfigUtil) {
    this.planConfigUtil = planConfigUtil;
  }

  public TestPlanFilter parseFilters(Path xtsRootPath, String type, String rootTestPlan)
      throws MobileHarnessException {
    if (rootTestPlan.equals("retry")) {
      // Skip parsing the retry test plan since it is not a valid XML.
      return TestPlanFilter.create(ImmutableSet.of(), ImmutableSet.of());
    }

    Path xtsTradefedJarPath =
        XtsDirUtil.getXtsToolsDir(xtsRootPath, type)
            .resolve(String.format("%s-tradefed.jar", type));
    return parseFilters(xtsTradefedJarPath, rootTestPlan);
  }

  @VisibleForTesting
  TestPlanFilter parseFilters(Path xtsTradefedJarPath, String rootTestPlan)
      throws MobileHarnessException {
    HashSet<String> includeFilters = new HashSet<>();
    HashSet<String> excludeFilters = new HashSet<>();
    HashSet<String> parsedTestPlans = new HashSet<>();

    Queue<Node> pendingNodes = new ArrayDeque<>();
    Queue<String> pendingTestPlans = new ArrayDeque<>();

    pendingTestPlans.offer(rootTestPlan);

    while (pendingTestPlans.peek() != null) {
      String testPlanName = pendingTestPlans.poll();
      Optional<Document> testPlan = planConfigUtil.loadConfig(testPlanName, xtsTradefedJarPath);
      parsedTestPlans.add(testPlanName);

      if (testPlan.isEmpty()) {
        // Skip the test plan since it is not valid.
        logger.atWarning().log(
            "Skip parsing the test plan: %s since it is not a valid test plan.", testPlanName);
        continue;
      }
      pendingNodes.offer(testPlan.get().getDocumentElement());
      while (pendingNodes.peek() != null) {
        Node node = pendingNodes.poll();

        switch (node.getNodeName()) {
          case CONFIGURATION_NODE_NAME:
            parseConfigurationNode(node, pendingNodes);
            break;
          case OPTION_NODE_NAME:
            parseOptionNode(node, includeFilters, excludeFilters);
            break;
          case INCLUDE_NODE_NAME:
            parseIncludeNode(node, parsedTestPlans, pendingTestPlans);
            break;
          default:
            break;
        }
      }
    }
    return TestPlanFilter.create(
        ImmutableSet.copyOf(includeFilters), ImmutableSet.copyOf(excludeFilters));
  }

  /**
   * Parse a node named configuration starting with "<configuration" which is the root node and
   * contains children nodes to be parsed.
   */
  private static void parseConfigurationNode(Node node, Queue<Node> pendingNodes) {
    NodeList children = node.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      pendingNodes.offer(children.item(i));
    }
  }

  /**
   * Parse a node named include starting with "<include" which may indicate another test plan should
   * be parsed.
   */
  private static void parseIncludeNode(
      Node node, Set<String> parsedTestPlans, Queue<String> pendingTestPlans) {
    NamedNodeMap attributes = node.getAttributes();
    if (attributes != null && attributes.getLength() == 1) {
      Node item = attributes.item(0);
      if (item.getNodeName().equals(ATTR_NAME_KEY)) {
        String newTestPlan = item.getNodeValue();
        if (!parsedTestPlans.contains(newTestPlan)) {
          pendingTestPlans.offer(newTestPlan);
        }
      }
    }
  }

  /**
   * Parse a node named option starting with "<option" which may contain filters. The following line
   * is an example.
   */
  private static void parseOptionNode(
      Node node, Set<String> includeFilters, Set<String> excludeFilters) {
    NamedNodeMap attributes = node.getAttributes();
    if (attributes != null) {
      int index = 0;
      while (index < attributes.getLength() - 1) {
        if (checkAndInsertFilter(
            attributes.item(index), attributes.item(index + 1), includeFilters, excludeFilters)) {
          // Skip current node and next node as they are qualified to be a filter.
          index += 2;
        } else {
          index++;
        }
      }
    }
  }

  /**
   * Insert the filter and return true the given items pair is qualified to be a include/exclude
   * filter. Return false otherwise.
   */
  private static boolean checkAndInsertFilter(
      Node nameNode, Node valueNode, Set<String> includeFilters, Set<String> excludeFilters) {
    if (!nameNode.getNodeName().equals(ATTR_NAME_KEY)
        || !valueNode.getNodeName().equals(ATTR_VALUE_KEY)) {
      return false;
    }

    switch (nameNode.getNodeValue()) {
      case INCLUDE_FILTER_ATTR_NAME:
        includeFilters.add(valueNode.getNodeValue());
        return true;
      case EXCLUDE_FILTER_ATTR_NAME:
        excludeFilters.add(valueNode.getNodeValue());
        return true;
      default:
        return false;
    }
  }

  /** A data class for all filters collected from the test plan. */
  @AutoValue
  public abstract static class TestPlanFilter {
    public abstract ImmutableSet<String> includeFilters();

    public abstract ImmutableSet<String> excludeFilters();

    public static TestPlanFilter create(
        ImmutableSet<String> includeFilters, ImmutableSet<String> excludeFilters) {
      return new AutoValue_TestPlanParser_TestPlanFilter(includeFilters, excludeFilters);
    }
  }
}
