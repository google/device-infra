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

load("@rules_java//java:java_library.bzl", "java_library")
load("//src/javatests/com/google/devtools/mobileharness/builddefs:junit_test_suites.bzl", "junit_test_suites")

package(
    default_applicable_licenses = ["//:license"],
)

java_library(
    name = "tests",
    testonly = 1,
    srcs = glob(["*.java"]),
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/config:config_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:check_device_write_permission_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:check_host_write_permission_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:get_device_config_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:get_host_config_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:get_host_default_device_config_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:update_device_config_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:update_host_config_handler",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
        "@maven//:junit_junit",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "small",
    ],
    deps = [":tests"],
)
