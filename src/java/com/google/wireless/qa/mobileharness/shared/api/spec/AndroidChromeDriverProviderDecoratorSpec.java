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

package com.google.wireless.qa.mobileharness.shared.api.spec;

import com.google.wireless.qa.mobileharness.shared.api.annotation.FileAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;

/**
 * Specs for {@link
 * com.google.wireless.qa.mobileharness.shared.api.decorator.AndroidChromeDriverProviderDecorator}.
 */
@SuppressWarnings("InterfaceWithOnlyStatics")
public interface AndroidChromeDriverProviderDecoratorSpec {

  @ParamAnnotation(
      required = false,
      help = "The target type of web test. Valid values: 'browser', 'webview'. Default: 'browser'.")
  public static final String PARAM_TARGET_TYPE = "target_type";

  @ParamAnnotation(required = false, help = "The package name of the app under test.")
  public static final String PARAM_PACKAGE_NAME = "package_name";

  @ParamAnnotation(
      required = false,
      help = "The browser package name when target_type is 'browser'.")
  public static final String PARAM_BROWSER_PACKAGE_NAME = "browser_package_name";

  @ParamAnnotation(
      required = false,
      help = "The WebView provider package name when target_type is 'webview'.")
  public static final String PARAM_WEBVIEW_PACKAGE_NAME = "webview_package_name";

  @ParamAnnotation(
      required = false,
      help = "The local workstation path to the compatible chromedriver binary.")
  public static final String PARAM_CHROMEDRIVER_PATH = "chromedriver_path";

  @FileAnnotation(
      required = false,
      help = "Candidate ChromeDriver binaries staged in the job's files dictionary.")
  public static final String FILE_CHROMEDRIVER_BINARIES = "chromedriver_binaries";

  @FileAnnotation(help = "The compatible ChromeDriver binary path staged by this decorator.")
  public static final String FILE_CHROMEDRIVER = "chromedriver";

  public static final String TARGET_TYPE_BROWSER = "browser";
  public static final String TARGET_TYPE_WEBVIEW = "webview";
  public static final String DEFAULT_CHROME_PACKAGE = "com.android.chrome";
  public static final String DEFAULT_WEBVIEW_PACKAGE = "com.google.android.webview";
  public static final String CHROMEDRIVER_BASE_DIR = "third_party/browser_automation/chromedriver";
  public static final String HEAD_DEPOT_CHROMEDRIVER_BASE_DIR =
      "/google/src/files/head/depot/google3/third_party/browser_automation/chromedriver";
  public static final String CHROMEDRIVER_BINARY_NAME = "chromedriver";
}
