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
    name = "providers_tests",
    testonly = 1,
    srcs = [
        "ConfigurationProviderImplTest.java",
        "LabInfoProviderImplTest.java",
        "NoOpConfigurationProviderTest.java",
        "NoOpRoutedUniverseLabInfoStubFactoryTest.java",
    ],
    deps = [
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_config_service_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:lab_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/common:common_resources_java_proto",
        "//src/devtools/mobileharness/shared/labinfo/proto:lab_info_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/util:config_service_capability",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:configuration_provider_impl",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:lab_info_provider_impl",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:no_op_configuration_provider",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:no_op_routed_universe_lab_info_stub_factory",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:routed_universe_lab_info_stub_factory",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/util",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub:lab_info",
        "//src/java/com/google/devtools/mobileharness/service/deviceconfig/rpc/stub:device_config_stub",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = ["small"],
    deps = [":providers_tests"],
)
