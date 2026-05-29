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

package com.google.devtools.mobileharness.platform.android.xts.businesslogic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/** Stores Business Logic rules and conditions. */
public final class BusinessLogic {

  // JSON keys
  private static final String BUSINESS_LOGIC_RULES_LISTS = "businessLogicRulesLists";
  private static final String TEST_NAME = "testName";
  private static final String BUSINESS_LOGIC_RULES = "businessLogicRules";
  private static final String RULE_CONDITIONS = "ruleConditions";
  private static final String RULE_ACTIONS = "ruleActions";
  private static final String METHOD_NAME = "methodName";
  private static final String METHOD_ARGS = "methodArgs";

  // A map from name to the business logic rules for that entity.
  private final ImmutableListMultimap<String, BusinessLogicRulesList> rules;

  private BusinessLogic(ImmutableListMultimap<String, BusinessLogicRulesList> rules) {
    this.rules = rules;
  }

  /**
   * Applies business logic for the given name.
   *
   * @param name The name to check rules for.
   * @param executor Execution handler for conditions and actions.
   */
  public void applyLogicFor(String name, BusinessLogicExecutor executor)
      throws ReflectiveOperationException {
    for (BusinessLogicRulesList rulesList : rules.get(name)) {
      rulesList.invokeRules(executor);
    }
  }

  /** Parses a BusinessLogic instance from a JSON string. */
  public static BusinessLogic fromJsonString(String businessLogicString)
      throws MobileHarnessException {
    ImmutableListMultimap.Builder<String, BusinessLogicRulesList> rulesMapBuilder =
        ImmutableListMultimap.builder();

    try {
      JsonObject root = JsonParser.parseString(businessLogicString).getAsJsonObject();

      if (!root.has(BUSINESS_LOGIC_RULES_LISTS)) {
        // no rules defined, return empty logic
        return new BusinessLogic(ImmutableListMultimap.of());
      }
      JsonArray jsonRulesLists = root.getAsJsonArray(BUSINESS_LOGIC_RULES_LISTS);

      for (JsonElement jsonRulesListElement : jsonRulesLists) {
        JsonObject jsonRulesList = jsonRulesListElement.getAsJsonObject();
        String testName = jsonRulesList.get(TEST_NAME).getAsString();
        rulesMapBuilder.put(testName, extractRulesList(jsonRulesList));
      }
    } catch (JsonParseException
        | IllegalStateException
        | ClassCastException
        | UnsupportedOperationException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_BUSINESS_LOGIC_SKIP_MODULE_DECORATOR_PARSE_JSON_ERROR,
          "Failed to parse business logic JSON",
          e);
    }

    return new BusinessLogic(rulesMapBuilder.build());
  }

  private static BusinessLogicRulesList extractRulesList(JsonObject rulesListJsonObject) {
    if (!rulesListJsonObject.has(BUSINESS_LOGIC_RULES)) {
      return new BusinessLogicRulesList(ImmutableList.of());
    }

    ImmutableList.Builder<BusinessLogicRule> rules = ImmutableList.builder();
    JsonArray rulesJsonArray = rulesListJsonObject.getAsJsonArray(BUSINESS_LOGIC_RULES);
    for (JsonElement ruleJsonElement : rulesJsonArray) {
      JsonObject ruleJsonObject = ruleJsonElement.getAsJsonObject();
      ImmutableList<BusinessLogicRuleCondition> ruleConditions =
          extractRuleConditionList(ruleJsonObject);
      ImmutableList<BusinessLogicRuleAction> ruleActions = extractRuleActionList(ruleJsonObject);
      rules.add(new BusinessLogicRule(ruleConditions, ruleActions));
    }
    return new BusinessLogicRulesList(rules.build());
  }

  private static ImmutableList<BusinessLogicRuleCondition> extractRuleConditionList(
      JsonObject ruleJsonObject) {
    if (!ruleJsonObject.has(RULE_CONDITIONS)) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<BusinessLogicRuleCondition> ruleConditions = ImmutableList.builder();
    JsonArray ruleConditionsJsonArray = ruleJsonObject.getAsJsonArray(RULE_CONDITIONS);
    for (JsonElement ruleConditionJsonElement : ruleConditionsJsonArray) {
      JsonObject ruleConditionJsonObject = ruleConditionJsonElement.getAsJsonObject();
      String methodName = ruleConditionJsonObject.get(METHOD_NAME).getAsString();
      boolean negated = false;
      if (methodName.startsWith("!")) {
        methodName = methodName.substring(1); // remove negation
        negated = true;
      }
      ruleConditions.add(
          new BusinessLogicRuleCondition(
              methodName, extractMethodArgs(ruleConditionJsonObject), negated));
    }
    return ruleConditions.build();
  }

  private static ImmutableList<BusinessLogicRuleAction> extractRuleActionList(
      JsonObject ruleJsonObject) {
    if (!ruleJsonObject.has(RULE_ACTIONS)) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<BusinessLogicRuleAction> ruleActions = ImmutableList.builder();
    JsonArray ruleActionsJsonArray = ruleJsonObject.getAsJsonArray(RULE_ACTIONS);
    for (JsonElement ruleActionJsonElement : ruleActionsJsonArray) {
      JsonObject ruleActionJsonObject = ruleActionJsonElement.getAsJsonObject();
      String methodName = ruleActionJsonObject.get(METHOD_NAME).getAsString();
      ruleActions.add(
          new BusinessLogicRuleAction(methodName, extractMethodArgs(ruleActionJsonObject)));
    }
    return ruleActions.build();
  }

  private static ImmutableList<String> extractMethodArgs(JsonObject jsonObject) {
    if (!jsonObject.has(METHOD_ARGS)) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<String> methodArgs = ImmutableList.builder();
    JsonArray methodArgsJsonArray = jsonObject.getAsJsonArray(METHOD_ARGS);
    for (JsonElement methodArgJsonElement : methodArgsJsonArray) {
      methodArgs.add(methodArgJsonElement.getAsString());
    }
    return methodArgs.build();
  }

  /** A list of BusinessLogicRules. */
  private static final class BusinessLogicRulesList {
    private final ImmutableList<BusinessLogicRule> rulesList;

    BusinessLogicRulesList(ImmutableList<BusinessLogicRule> rulesList) {
      this.rulesList = rulesList;
    }

    void invokeRules(BusinessLogicExecutor executor) throws ReflectiveOperationException {
      for (BusinessLogicRule rule : rulesList) {
        if (rule.invokeConditions(executor)) {
          rule.invokeActions(executor);
        }
      }
    }
  }

  /** A Business Logic Rule holding conditions and actions. */
  private static final class BusinessLogicRule {
    private final ImmutableList<BusinessLogicRuleCondition> conditions;
    private final ImmutableList<BusinessLogicRuleAction> actions;

    BusinessLogicRule(
        ImmutableList<BusinessLogicRuleCondition> conditions,
        ImmutableList<BusinessLogicRuleAction> actions) {
      this.conditions = conditions;
      this.actions = actions;
    }

    boolean invokeConditions(BusinessLogicExecutor executor) throws ReflectiveOperationException {
      for (BusinessLogicRuleCondition condition : conditions) {
        if (!condition.invoke(executor)) {
          return false;
        }
      }
      return true;
    }

    void invokeActions(BusinessLogicExecutor executor) throws ReflectiveOperationException {
      for (BusinessLogicRuleAction action : actions) {
        action.invoke(executor);
      }
    }
  }

  /** A Business Logic Rule Condition executing a boolean method. */
  private static final class BusinessLogicRuleCondition {
    private final String methodName;
    private final ImmutableList<String> methodArgs;
    private final boolean negated;

    BusinessLogicRuleCondition(
        String methodName, ImmutableList<String> methodArgs, boolean negated) {
      this.methodName = methodName;
      this.methodArgs = methodArgs;
      this.negated = negated;
    }

    boolean invoke(BusinessLogicExecutor executor) throws ReflectiveOperationException {
      boolean result = executor.executeCondition(methodName, methodArgs.toArray(String[]::new));
      return negated != result;
    }
  }

  /** A Business Logic Rule Action executing a void method. */
  private static final class BusinessLogicRuleAction {
    private final String methodName;
    private final ImmutableList<String> methodArgs;

    BusinessLogicRuleAction(String methodName, ImmutableList<String> methodArgs) {
      this.methodName = methodName;
      this.methodArgs = methodArgs;
    }

    void invoke(BusinessLogicExecutor executor) throws ReflectiveOperationException {
      executor.executeAction(methodName, methodArgs.toArray(String[]::new));
    }
  }
}
