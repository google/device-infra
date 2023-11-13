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

package com.google.devtools.mobileharness.infra.controller.device;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.Dispatcher;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The manager used to manage the dependencies between different dispatchers and provided the
 * topological sorted dispatchers to device manager.
 */
public final class DispatcherManager {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static class SingletonHolder {
    private static final DispatcherManager singleton = new DispatcherManager();
  }

  /**
   * To bundle an element with a mutable structure of the dependency graph.
   *
   * <p>Each {@link InternalElement} counts how many predecessors it has left. Rather than keep a
   * list of predecessors, we reverse the relation so that it's easy to navigate to the successors
   * when an {@link InternalElement} is selected for sorting.
   *
   * <p>This maintains a {@code originalIndex} to allow a "stable" sort based on the original
   * position in the input list.
   */
  private static final class InternalElement implements Comparable<InternalElement> {
    final String element;
    final int originalIndex;
    final List<InternalElement> successors;
    int predecessorCount;

    InternalElement(String element, int originalIndex) {
      this.element = element;
      this.originalIndex = originalIndex;
      this.successors = new ArrayList<>();
    }

    @Override
    public int compareTo(InternalElement o) {
      return Integer.compare(originalIndex, o.originalIndex);
    }
  }

  public static DispatcherManager getInstance() {
    return SingletonHolder.singleton;
  }

  private final ConcurrentHashMap<String, Class<? extends Dispatcher>> dispatcherNameToTypes =
      new ConcurrentHashMap<>();

  private final MutableGraph<String> graphs;

  @VisibleForTesting
  DispatcherManager() {
    graphs = GraphBuilder.directed().allowsSelfLoops(false).build();
  }

  /**
   * Registes the dispatcher to the topological graph.
   *
   * @return {@code true} if the graph was modified as a result of this call
   */
  @CanIgnoreReturnValue
  public boolean add(Class<? extends Dispatcher> dispatcherType) {
    dispatcherNameToTypes.put(dispatcherType.getSimpleName(), dispatcherType);
    return graphs.addNode(dispatcherType.getSimpleName());
  }

  /**
   * Adds the dependencies of dispatcher. {@code dependDispatcherSimpleNames} are dispatched prior
   * to {@code dispatcherSimpleName}.
   */
  public void addDependencies(
      String dispatcherSimpleName, List<String> dependDispatcherSimpleNames) {
    dependDispatcherSimpleNames.forEach(
        dependDispatcherSimpleName ->
            addDependency(dispatcherSimpleName, dependDispatcherSimpleName));
  }

  /**
   * Adds the dependency of a dispatcher. {@code dependDispatcherSimpleName} is dispatched prior to
   * {@code dispatcherSimpleName}.
   */
  public void addDependency(String dispatcherSimpleName, String dependDispatcherSimpleName) {
    graphs.putEdge(dependDispatcherSimpleName, dispatcherSimpleName);
  }

  /** Returns the sorted dispatchers in topological order. */
  public ImmutableList<Class<? extends Dispatcher>> getAllDispatchersInOrder() {
    return topologicalOrdering().stream()
        .filter(internalElement -> dispatcherNameToTypes.containsKey(internalElement.element))
        .map(internalElement -> dispatcherNameToTypes.get(internalElement.element))
        .collect(toImmutableList());
  }

  /**
   * Returns a topological ordering of the nodes in {@code graph}.
   *
   * <p>If multiple valid topological orderings exist, returns the lexicographically least ordering
   * based on the order of nodes.
   */
  private List<InternalElement> topologicalOrdering() {
    List<InternalElement> internalElements = internalizeElements();
    List<InternalElement> sortedElements = new ArrayList<>(internalElements.size());
    PriorityQueue<InternalElement> readyElements = new PriorityQueue<>();
    for (InternalElement element : internalElements) {
      if (element.predecessorCount == 0) {
        readyElements.add(element);
      }
    }

    while (!readyElements.isEmpty()) {
      InternalElement currentElement = readyElements.poll();
      sortedElements.add(currentElement);
      for (InternalElement successor : currentElement.successors) {
        successor.predecessorCount--;
        if (successor.predecessorCount == 0) {
          readyElements.add(successor);
        }
      }
    }

    if (sortedElements.size() != internalElements.size()) {
      logger.atWarning().log("Detects cycle in the graph, ignores the nodes in the cycle");
    }
    return sortedElements;
  }

  /**
   * Internalizes the elements of the input list, representing the dependency structure to make
   * topological sort easier to compute.
   *
   * @return a list of {@link InternalElement}s initialized with dependency structure.
   */
  private List<InternalElement> internalizeElements() {
    List<String> elements = new ArrayList<>(graphs.nodes());
    List<InternalElement> internalElements = new ArrayList<>(elements.size());
    Map<String, InternalElement> internalElementsByValue = new HashMap<>();
    int index = 0;
    for (String element : elements) {
      InternalElement internalElement = new InternalElement(element, index);
      internalElements.add(internalElement);
      internalElementsByValue.put(element, internalElement);
      index++;
    }

    for (InternalElement internalElement : internalElements) {
      graphs
          .predecessors(internalElement.element)
          .forEach(
              predecessor -> {
                if (internalElementsByValue.containsKey(predecessor)) {
                  internalElementsByValue.get(predecessor).successors.add(internalElement);
                  internalElement.predecessorCount++;
                }
              });
    }
    return internalElements;
  }
}
