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
    name = "fake_batch_fleet",
    testonly = 1,
    srcs = ["FakeBatchFleet.java"],
    visibility = [
        "//src/javatests/com/google/devtools/mobileharness/fe/v6/service/config:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/shared/labinfo/proto:lab_info_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/auth",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:config_result",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:configuration_provider",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:lab_info_provider",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/util",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_library(
    name = "tests",
    testonly = 1,
    srcs = glob(
        ["*.java"],
        exclude = ["FakeBatchFleet.java"],
    ),
    deps = [
        ":fake_batch_fleet",
        "//src/devtools/mobileharness/api/deviceconfig/proto:basic_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/admin:admin_resources_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/common:common_resources_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/config:config_resources_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/config:config_service_java_proto",
        "//src/devtools/mobileharness/shared/labinfo/proto:lab_info_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:batch_device_config_helper",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:batch_get_configurable_dimension_keys_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:batch_get_device_dimension_configs_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:batch_get_device_wifi_configs_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:batch_update_device_dimension_configs_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:batch_update_device_wifi_configs_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:check_device_config_permission_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:check_device_write_permission_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:check_host_config_permission_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:check_host_write_permission_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:get_configurable_dimensions_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:get_device_config_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:get_dimension_value_suggestions_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:get_host_config_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:get_host_default_device_config_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:get_recommended_wifi_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:get_wifi_suggestions_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:unlock_host_properties_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:update_device_config_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/handlers:update_host_config_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/util:config_pusher_helper",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/util:config_service_capability",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/errors",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/auth",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:config_result",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:configuration_provider",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:lab_info_provider",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:wifi_credentials_store",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/util",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@grpc-java//core",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_extensions_guice_testlib",
        "@maven//:com_google_inject_guice",
        "@maven//:com_google_protobuf_protobuf_java",
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
    deps = [":tests"],
)
