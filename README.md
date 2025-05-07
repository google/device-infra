# Android Test Station Utilities

Utilities for **Android Test Station**. Android Test Station is a testing tool
that Android developers and test engineers can use to employ a user interface
for running standard Android test suites, such as the Android Compatibility Test
Suite (CTS).

## Build locally

Install Java 17 if it’s not already installed:

```
sudo apt-get install openjdk-17-jdk
```

Set Java 17 to be used (by Bazel):

```
sudo update-java-alternatives --set /usr/lib/jvm/java-1.17.0-openjdk-amd64
export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-amd64
```

Install [Bazelisk](https://github.com/bazelbuild/bazelisk):

```
npm install -g @bazel/bazelisk
which bazelisk
bazelisk --version
```

Set up Android SDK:

```
export ANDROID_HOME=/usr/lib/deviceinfra-android-sdk
wget -nv https://dl.google.com/android/repository/commandlinetools-linux-7583922_latest.zip && \
unzip commandlinetools-linux-7583922_latest.zip && \
rm commandlinetools-linux-7583922_latest.zip && \
sudo mkdir -p $ANDROID_HOME/cmdline-tools/latest && \
sudo mv cmdline-tools/* $ANDROID_HOME/cmdline-tools/latest && \
rm -r cmdline-tools && \
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

Change directory into where the deviceinfra source code is:

```
cd device-infra/
```

Build the source code:

```
bazelisk build --keep_going --build_tag_filters=-deviceinfra_disable_in_kokoro src/...
```

Relevant artifacts can be found at:

```
bazel-bin/src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver/ats_olc_server_deploy.jar
bazel-bin/src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver/ats_olc_server_local_mode_deploy.jar
bazel-bin/src/java/com/google/devtools/mobileharness/infra/ats/console/ats_console_deploy.jar
bazel-bin/src/java/com/google/devtools/mobileharness/infra/ats/local/ats_local_runner_deploy.jar
bazel-bin/src/java/com/google/devtools/mobileharness/infra/lab/lab_server_oss_deploy.jar
```

## Run an example

To run an example helloworld test using these jars built, you may reference the
[OlcServerIntegrationTest](https://github.com/google/device-infra/blob/f96966f3ba4d8bf614d64c08d06bb200d50cd8d5/src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice/OlcServerIntegrationTest.java)
file and follow how these binaries are started.
