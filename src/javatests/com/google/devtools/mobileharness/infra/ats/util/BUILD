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

java_test(
    name = "DeviceConfigUtilTest",
    size = "small",
    srcs = ["DeviceConfigUtilTest.java"],
    deps = [
        "//src/devtools/mobileharness/infra/ats/server/proto:conditioned_device_config_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/ats/util:device_config_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:android_real_device",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:junit_junit",
    ],
)
