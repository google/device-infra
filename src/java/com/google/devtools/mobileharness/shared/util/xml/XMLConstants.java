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

package com.google.devtools.mobileharness.shared.util.xml;

import java.util.Set;
import java.util.regex.Pattern;

/** constants for test results xml. */
@SuppressWarnings("JdkImmutableCollections")
public final class XMLConstants {
  private XMLConstants() {}

  //// xml element names
  public static final String ELEMENT_TESTSUITES = "testsuites";
  public static final String ELEMENT_TESTSUITE = "testsuite";
  public static final String ELEMENT_TESTCASE = "testcase";
  public static final String ELEMENT_TESTDECORATOR = "testdecorator";
  public static final String ELEMENT_PROPERTIES = "properties";
  public static final String ELEMENT_PROPERTY = "property";
  public static final String ELEMENT_FAILURE = "failure";
  public static final String ELEMENT_SKIPPED = "skipped";
  public static final String ELEMENT_ERROR = "error";
  public static final String ELEMENT_EXPECTED = "expected";
  public static final String ELEMENT_ACTUAL = "actual";
  public static final String ELEMENT_VALUE = "value";
  public static final String ELEMENT_SYSTEM_OUT = "system-out";
  public static final String ELEMENT_SYSTEM_ERR = "system-err";
  public static final String ELEMENT_EXTERNAL_LINKS = "externallinks";
  public static final String ELEMENT_EXTERNAL_LINK = "externallink";
  public static final String ELEMENT_TEST_COMPONENT = "testcomponent";
  public static final String ELEMENT_TEST_COMPONENTS = "testcomponents";

  //// xml attribute names

  /* matches valid XML attribute names */
  public static final Pattern XML_ATTRIBUTE_NAME = Pattern.compile("[A-Za-z_](\\w|-|\\.)*");

  public static final String ATTR_TESTSUITE_NAME = "name";
  public static final String ATTR_TESTSUITE_TESTS = "tests";
  public static final String ATTR_TESTSUITE_FAILURES = "failures";
  public static final String ATTR_TESTSUITE_ERRORS = "errors";
  public static final String ATTR_TESTSUITE_TIME = "time";
  public static final String ATTR_TESTSUITE_TIMESTAMP = "timestamp";

  public static final String ATTR_TESTDECORATOR_CLASSNAME = "classname";
  public static final String ATTR_TESTDECORATOR_TESTS = "tests";
  public static final String ATTR_TESTDECORATOR_FAILURES = "failures";
  public static final String ATTR_TESTDECORATOR_ERRORS = "errors";
  public static final String ATTR_TESTDECORATOR_TIME = "time";
  public static final String ATTR_TESTDECORATOR_TIMESTAMP = "timestamp";

  public static final String ATTR_TESTCASE_NAME = "name";
  public static final String ATTR_TESTCASE_CLASSNAME = "classname";
  public static final String ATTR_TESTCASE_STATUS = "status";
  public static final String ATTR_TESTCASE_RESULT = "result";
  public static final String ATTR_TESTCASE_TIME = "time";
  public static final String ATTR_TESTCASE_TIMESTAMP = "timestamp";
  public static final String ATTR_TESTCASE_REPEATNUMBER = "repeatnumber";
  public static final String ATTR_TESTCASE_RETRYNUMBER = "retrynumber";

  public static final String ATTR_PROPERTY_NAME = "name";
  public static final String ATTR_PROPERTY_VALUE = "value";

  public static final String ATTR_TESTCOMPONENT_NAME = "name";

  public static final String ATTR_EXTERNAL_TOOL_CONTACT_EMAIL = "contact_email";
  public static final String ATTR_EXTERNAL_TOOL_COMPONENT_ID = "component_id";
  public static final String ATTR_EXTERNAL_TOOL_URL = "url";
  public static final String ATTR_EXTERNAL_TOOL_ICON_URL = "icon_url";
  public static final String ATTR_EXTERNAL_TOOL_ICON_NAME = "icon_name";
  public static final String ATTR_EXTERNAL_TOOL_DESCRIPTION = "description";
  public static final String ATTR_EXTERNAL_TOOL_FOREGROUND_COLOR = "foreground_color";
  public static final String ATTR_EXTERNAL_TOOL_BACKGROUND_COLOR = "background_color";

  public static final String ATTR_FAILURE_MESSAGE = "message";
  public static final String ATTR_FAILURE_TYPE = "type";

  public static final String ATTR_ERROR_MESSAGE = "message";
  public static final String ATTR_ERROR_TYPE = "type";
  public static final String ATTR_ERROR_TESTCOMPONENT = "testcomponent";

  //// xml attribute values
  /*
   * Test case was not started because the test harness run was interrupted by a signal.interrupted
   * or timed out.
   */
  public static final String RESULT_CANCELLED = "cancelled";
  /*
   * Test case was run and completed (possibly failing or throwing an exception, but not
   * interrupted).
   */
  public static final String RESULT_COMPLETED = "completed";
  /*
   * Test case was started but not completed because the test harness received a signal and decided
   * to stop running tests.
   */
  public static final String RESULT_INTERRUPTED = "interrupted";
  /*
   * Test case was not run because the test was labeled in the code as suppressed (for example, the
   * test was broken and someone wanted to temporarily wire it off).
   */
  public static final String RESULT_SUPPRESSED = "suppressed";

  /** Test case was skipped. */
  public static final String RESULT_SKIPPED = "skipped";

  public static final String STATUS_RUN = "run";
  public static final String STATUS_NOTRUN = "notrun";

  /* tests can't emit properties with these names, in order to produce valid xml */
  public static final Set<String> ILLEGAL_TEST_PROPERTIES =
      Set.of(
          ATTR_TESTCASE_NAME,
          ATTR_TESTCASE_STATUS,
          ATTR_TESTCASE_CLASSNAME,
          ATTR_TESTCASE_TIME,
          ATTR_TESTCASE_RESULT);
} // XMLConstants
