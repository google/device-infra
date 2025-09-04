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

load("@rules_java//java:defs.bzl", "java_library")
load("//src/javatests/com/google/devtools/mobileharness/builddefs:junit_test_suites.bzl", "junit_test_suites")

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = 1,
)

java_library(
    name = "ats",
    srcs = glob(["*.java"]),
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/devtools/mobileharness/api/query/proto:filter_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_record_java_proto",
        "//src/devtools/mobileharness/infra/master/rpc/proto:job_sync_service_java_proto",
        "//src/devtools/mobileharness/infra/master/rpc/proto:lab_sync_service_java_proto",
        "//src/devtools/mobileharness/shared/labinfo/proto:lab_info_service_java_proto",
        "//src/devtools/mobileharness/shared/version/proto:version_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/model/job:locator",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:schedule_unit",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/allocation/allocator",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/device:querier",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/ats:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/ats:ats_mode",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/ats:device_querier",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/ats:job_sync_service",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/ats:lab_record_manager",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/ats:module",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/ats:remote_device_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler:abstract_scheduler",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub/grpc:lab_info",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub/grpc:lab_sync",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:channel_factory",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:master_grpc_stub_helper",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "//src/java/com/google/devtools/mobileharness/shared/util/port:portprober",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
        "//src/java/com/google/devtools/mobileharness/shared/version",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job:job_schedule_unit",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:net_util",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@grpc-java//core",
        "@grpc-java//netty",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_guava_guava_testlib",
        "@maven//:com_google_inject_extensions_guice_testlib",
        "@maven//:com_google_inject_guice",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_google_truth_extensions_truth_proto_extension",
        "@maven//:javax_inject_jsr330_api",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "small",
    ],
    deps = [":ats"],
)
