workspace(name = "com_google_deviceinfra")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

# GoogleTest/GoogleMock framework. Used by most unit-tests.
http_archive(
    name = "com_google_googletest",
    patches = ["//fcp/patches:googletest.patch"],
    sha256 = "fcfac631041fce253eba4fc014c28fd620e33e3758f64f8ed5487cc3e1840e3d",
    strip_prefix = "googletest-5a509dbd2e5a6c694116e329c5a20dc190653724",
    urls = ["https://github.com/google/googletest/archive/5a509dbd2e5a6c694116e329c5a20dc190653724.zip"],
)

# Proto library target in bazel.
http_archive(
    name = "rules_proto",
    sha256 = "e017528fd1c91c5a33f15493e3a398181a9e821a804eb7ff5acdd1d2d6c2b18d",
    strip_prefix = "rules_proto-4.0.0-3.20.0",
    urls = [
        "https://github.com/bazelbuild/rules_proto/archive/refs/tags/4.0.0-3.20.0.tar.gz",
    ],
)

# Android bazel rules
http_archive(
    name = "build_bazel_rules_android",
    urls = ["https://github.com/bazelbuild/rules_android/archive/v0.1.1.zip"],
    sha256 = "cd06d15dd8bb59926e4d65f9003bfc20f9da4b2519985c27e190cddc8b7a7806",
    strip_prefix = "rules_android-0.1.1",
)

# Configure Android SDK Path
load("@build_bazel_rules_android//android:rules.bzl", "android_sdk_repository")
android_sdk_repository(name = "androidsdk")

load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies", "rules_proto_toolchains")

rules_proto_dependencies()

rules_proto_toolchains()

http_archive(
    name = "bazel_skylib",
    sha256 = "f7be3474d42aae265405a592bb7da8e171919d74c16f082a5457840f06054728",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-skylib/releases/download/1.2.1/bazel-skylib-1.2.1.tar.gz",
        "https://github.com/bazelbuild/bazel-skylib/releases/download/1.2.1/bazel-skylib-1.2.1.tar.gz",
    ],
)

http_archive(
    name = "bazel_tools",
    sha256 = "820a94dbb14071ed6d8c266cf0c080ecb265a5eea65307579489c4662c2d582a",
    urls = [
        "https://github.com/bazelbuild/bazel/releases/download/5.2.0/bazel-5.2.0-dist.zip",
    ],
)

# Java Maven-based repositories.
http_archive(
    name = "rules_jvm_external",
    sha256 = "cd1a77b7b02e8e008439ca76fd34f5b07aecb8c752961f9640dea15e9e5ba1ca",
    strip_prefix = "rules_jvm_external-4.2",
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/4.2.zip",
)

load("@rules_jvm_external//:repositories.bzl", "rules_jvm_external_deps")

rules_jvm_external_deps()

load("@rules_jvm_external//:setup.bzl", "rules_jvm_external_setup")

rules_jvm_external_setup()

load("@rules_jvm_external//:defs.bzl", "maven_install")

maven_install(
    artifacts = [
        "com.beust:jcommander:1.82",
        "com.google.auto.value:auto-value:1.9",
        "com.google.auto.value:auto-value-annotations:1.9",
        "com.google.code.findbugs:jsr305:3.0.2",
        "com.google.errorprone:error_prone_annotations:2.11.0",
        "com.google.flogger:flogger-system-backend:0.7.4",
        "com.google.flogger:flogger:0.7.4",
        "com.google.code.gson:gson:2.9.1",
        "com.google.guava:guava:31.1-jre",
        "com.google.inject.extensions:guice-assistedinject:5.1.0",
        "com.google.inject:guice:4.1.0",
        "com.google.testparameterinjector:test-parameter-injector:1.10",
        "com.google.truth.extensions:truth-proto-extension:1.1.3",
        "com.google.truth.extensions:truth-java8-extension:1.1.3",
        "com.google.truth:truth:1.1.3",
        "javax.inject:jsr330-api:0.9",
        "junit:junit:4.13",
        "org.apache.commons:commons-lang3:3.6",
        "org.mockito:mockito-core:4.3.1",
        "org.reflections:reflections:0.9.10",
    ],
    fetch_sources = True,
    repositories = [
        "https://maven.google.com",
        "https://repo1.maven.org/maven2",
        "https://repository.mulesoft.org/nexus/content/repositories/public",
    ],
)
