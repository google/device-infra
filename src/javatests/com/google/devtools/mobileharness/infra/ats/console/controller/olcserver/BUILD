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
    name = "olcserver",
    srcs = glob(["*.java"]),
    data = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:ats_olc_server_deploy.jar",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/ats/console/controller/proto:session_plugin_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:ats_session_stub",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:module",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:server_preparer",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:session_stub",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_factory_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/port:portprober",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/javatests/com/google/devtools/deviceinfra/builddefs:truth",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/runfiles:runfiles_util",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_extensions_guice_testlib",
        "@maven//:com_google_inject_guice",
        "@maven//:com_google_truth_extensions_truth_proto_extension",
        "@maven//:javax_inject_jsr330_api",
        "@maven//:junit_junit",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "medium",
    ],
    deps = [":olcserver"],
)
