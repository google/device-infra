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

load("//src/javatests/com/google/devtools/deviceinfra/builddefs:junit_test_suites.bzl", "junit_test_suites")

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = 1,
)

java_library(
    name = "sessionplugin",
    srcs = glob(["*.java"]),
    data = [
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/local:testdata",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/devtools/mobileharness/infra/ats/local/proto:session_plugin_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/local/sessionplugin:ats_local_session_plugin",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_event",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_info",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/config:module_configuration_helper",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "//src/javatests/com/google/devtools/deviceinfra/builddefs:truth",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/runfiles:runfiles_util",
        "@com_google_protobuf_protobuf_java",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_extensions_guice_testlib",
        "@maven//:com_google_inject_guice",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "small",
    ],
    deps = [":sessionplugin"],
)
