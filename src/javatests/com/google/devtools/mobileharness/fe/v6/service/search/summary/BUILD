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
    default_visibility = ["//src/devtools/mobileharness/fe/v6:visibility"],
)

java_test(
    name = "SmallTests",
    size = "small",
    srcs = glob(["*Test.java"]),
    test_class = "com.google.devtools.mobileharness.fe.v6.service.search.summary.NoOpGlobalSummaryProviderTest",
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_summary_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/errors",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/summary:noop_global_summary_provider",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@grpc-java//core",
        "@maven//:junit_junit",
    ],
)
