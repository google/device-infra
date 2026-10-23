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

load("@rules_java//java:java_test.bzl", "java_test")

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = 1,
)

java_test(
    name = "SmallTests",
    size = "small",
    srcs = ["MobileHarnessLogHandlerTest.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/logging/controller/handler",
        "//src/java/com/google/devtools/mobileharness/shared/logging/controller/queue",
        "//src/java/com/google/devtools/mobileharness/shared/logging/util:log_entry_util",
        "@maven//:com_google_api_grpc_proto_google_cloud_logging_v2",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_test(
    name = "MediumTests",
    size = "medium",
    srcs = ["LocalFileHandlerModuleTest.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/logging/controller/handler",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_inject_guice",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)
