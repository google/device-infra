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
    name = "util_tests",
    testonly = 1,
    srcs = [
        "ConfigServiceCapabilityTest.java",
        "DeviceConfigConverterTest.java",
    ],
    deps = [
        "//src/devtools/mobileharness/api/deviceconfig/proto:basic_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/config:config_resources_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/util:config_service_capability",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/util:device_config_converter",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/util",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = ["small"],
    deps = [":util_tests"],
)
