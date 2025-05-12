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

package com.google.wireless.qa.mobileharness.shared.sponge;


/** Utility class for XML constants. */
public final class XmlConstantsHelper {

  private XmlConstantsHelper() {}

  public static String getElementTestsuite() {
    return com.google.devtools.mobileharness.shared.util.xml.XMLConstants.ELEMENT_TESTSUITE;
  }

  public static String getElementTestsuites() {
    return com.google.devtools.mobileharness.shared.util.xml.XMLConstants.ELEMENT_TESTSUITES;
  }

  public static String getElementTestcase() {
    return com.google.devtools.mobileharness.shared.util.xml.XMLConstants.ELEMENT_TESTCASE;
  }

  public static String getElementProperties() {
    return com.google.devtools.mobileharness.shared.util.xml.XMLConstants.ELEMENT_PROPERTIES;
  }

  public static String getElementProperty() {
    return com.google.devtools.mobileharness.shared.util.xml.XMLConstants.ELEMENT_PROPERTY;
  }

  public static String getElementError() {
    return com.google.devtools.mobileharness.shared.util.xml.XMLConstants.ELEMENT_ERROR;
  }

  public static String getElementFailure() {
    return com.google.devtools.mobileharness.shared.util.xml.XMLConstants.ELEMENT_FAILURE;
  }

  public static String getAttrTestcomponentName() {
    return com.google.devtools.mobileharness.shared.util.xml.XMLConstants.ATTR_TESTCOMPONENT_NAME;
  }

  public static String getAttrTestcaseClassname() {
    return com.google.devtools.mobileharness.shared.util.xml.XMLConstants.ATTR_TESTCASE_CLASSNAME;
  }

  public static String getAttrTestcaseResult() {
    return com.google.devtools.mobileharness.shared.util.xml.XMLConstants.ATTR_TESTCASE_RESULT;
  }

  public static String getAttrTestcaseTime() {
    return com.google.devtools.mobileharness.shared.util.xml.XMLConstants.ATTR_TESTCASE_TIME;
  }

  public static String getAttrTestcaseTimestamp() {
    return com.google.devtools.mobileharness.shared.util.xml.XMLConstants.ATTR_TESTCASE_TIMESTAMP;
  }

  public static String getAttrTestcaseName() {
    return com.google.devtools.mobileharness.shared.util.xml.XMLConstants.ATTR_TESTCASE_NAME;
  }

  public static String getAttrPropertyName() {
    return com.google.devtools.mobileharness.shared.util.xml.XMLConstants.ATTR_PROPERTY_NAME;
  }

  public static String getAttrPropertyValue() {
    return com.google.devtools.mobileharness.shared.util.xml.XMLConstants.ATTR_PROPERTY_VALUE;
  }

  public static String getResultSkipped() {
    return com.google.devtools.mobileharness.shared.util.xml.XMLConstants.RESULT_SKIPPED;
  }
}
