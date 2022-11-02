# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

java_library(
    name = "exception",
    srcs = [
        "MobileHarnessUserPluginException.java",
        "SkipJobException.java",
        "SkipTestException.java",
    ],
    tags = [
        "alt_dep=//java/com/google/devtools/mobileharness/api/testrunner/plugin:exception",
        "avoid_dep",
    ],
    visibility = ["//java/com/google/devtools/mobileharness/api/testrunner/plugin:plugin_exception_users"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "skip_exception_factory",
    srcs = [
        "SkipExceptionFactory.java",
    ],
    visibility = [
        # The skip exception factory which can throw widely result, for MH internal use only.
        "//java/com/google/devtools/mobileharness/infra/client/bin/pesto/api:__subpackages__",
        "//java/com/google/devtools/mobileharness/platform/android/app/store:__subpackages__",
        "//java/com/google/devtools/mobileharness/platform/android/appcrawler:__subpackages__",
        "//javatests/com/google/wireless/qa/mobileharness/shared/controller:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__pkg__",
    ],
    deps = [
        ":exception",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "annotation",
    srcs = [
        "SubscribeEventOfAllDrivers.java",
    ],
    visibility = [
        "//java/com/google/devtools/build/buildjar/plugin/mobileharness:__pkg__",
        "//java/com/google/devtools/mobileharness:__subpackages__",
        "//javatests/com/google/devtools/build/buildjar/plugin/mobileharness:__pkg__",
        "//javatests/com/google/devtools/mobileharness:__subpackages__",
        "//src/java/com/google/devtools/mobileharness:__subpackages__",
    ],
)
