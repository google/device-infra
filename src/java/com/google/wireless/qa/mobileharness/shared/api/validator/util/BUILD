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

java_library(
    name = "android_decorator_validator_util",
    srcs = [
        "AndroidDecoratorValidatorUtil.java",
    ],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
    ],
)
