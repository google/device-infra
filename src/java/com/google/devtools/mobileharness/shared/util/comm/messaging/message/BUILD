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

# Do not make it public.
java_library(
    name = "message",
    srcs = ["TestMessageInfo.java"],
    visibility = [
        "//java/com/google/devtools/mobileharness/infra/lab/controller:__subpackages__",
        "//java/com/google/devtools/mobileharness/infra/lab/rpc/service:__subpackages__",
        "//java/com/google/devtools/mobileharness/shared/util/comm/messaging:__subpackages__",
        "//java/com/google/wireless/qa/mobileharness/client/api/mode/remote:__subpackages__",
        "//java/com/google/wireless/qa/mobileharness/shared/comm/message:__subpackages__",
        "//javatests/com/google/alkali/applications/xcid/server/acid/actions:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/infra/lab/controller:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/infra/lab/rpc/service:__subpackages__",
        "//javatests/com/google/wireless/qa/mobileharness/client/api/mode/remote:__subpackages__",
        "//javatests/com/google/wireless/qa/mobileharness/shared/comm/message:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/messaging:__subpackages__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/deviceinfra/shared/util/auto:auto_value",
        "@maven//:com_google_guava_guava",
    ],
)
