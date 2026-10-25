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

package(default_applicable_licenses = ["//:license"])

java_test(
    name = "SmallTests",
    size = "small",
    srcs = [
        "FlagParserTest.java",
        "ProtoHelperTest.java",
        "TimeoutMonitorTest.java",
    ],
    data = ["//src/javatests/com/google/devtools/deviceaction/testdata"],
    deps = [
        "//src/devtools/deviceaction/schemas/proto:base_spec_java_proto",
        "//src/devtools/deviceaction/schemas/proto/actions:install_mainline_spec_java_proto",
        "//src/devtools/deviceaction/schemas/proto/actions:reset_spec_java_proto",
        "//src/java/com/google/devtools/deviceaction/common/error",
        "//src/java/com/google/devtools/deviceaction/common/schemas",
        "//src/java/com/google/devtools/deviceaction/common/utils",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/runfiles:runfiles_util",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_extensions_truth_proto_extension",
        "@maven//:junit_junit",
    ],
)

java_test(
    name = "MediumTests",
    size = "medium",
    srcs = [
        "BundletoolUtilTest.java",
        "CommandHistoryWriterTest.java",
        "CompositeResolverTest.java",
        "GCSResolverTest.java",
        "ResourceUtilTest.java",
        "SimpleResolverTest.java",
    ],
    data = ["//src/javatests/com/google/devtools/deviceaction/testdata"],
    deps = [
        "//src/devtools/deviceaction/schemas/proto:base_spec_java_proto",
        "//src/java/com/google/devtools/deviceaction/common/error",
        "//src/java/com/google/devtools/deviceaction/common/utils",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/command/history",
        "//src/java/com/google/devtools/mobileharness/shared/util/command/testing",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_apis_google_api_services_storage",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)
