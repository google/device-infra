/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.mobileharness.platform.android.media;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import static org.junit.Assert.assertThrows;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import com.google.testing.junit.testparameterinjector.TestParameters.TestParametersValues;
import com.google.testing.junit.testparameterinjector.TestParametersValuesProvider;
import java.util.ArrayList;
import java.util.stream.IntStream;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class ScreenRecordArgsTest {

  static final class BuildArgsTestParametersProvider extends TestParametersValuesProvider {
    @Override
    public ImmutableList<TestParametersValues> provideValues(Context context) {

      String[] parameterNames =
          new String[] {"bitRate", "size", "verbose", "bugreport", "argsExpected"};
      Object[][] data =
          new Object[][] {
            {null, "1280x720", false, false, "--size 1280x720"},
            {4000000, null, false, false, "--bit-rate 4000000"},
            {null, null, true, false, "--verbose"},
            {null, null, false, true, "--bugreport"},
            {null, null, true, true, "--verbose --bugreport"},
          };

      ArrayList<TestParametersValues> cases = new ArrayList<>();
      for (Object[] parameters : data) {
        TestParametersValues.Builder builder = TestParametersValues.builder();
        IntStream.range(0, parameterNames.length)
            .forEach(
                i ->
                    builder
                        .name(stream(parameters).map(String::valueOf).collect(joining(",")))
                        .addParameter(parameterNames[i], parameters[i]));
        cases.add(builder.build());
      }

      return ImmutableList.copyOf(cases);
    }
  }

  @Test
  @TestParameters(valuesProvider = BuildArgsTestParametersProvider.class)
  public void testBuildArgs(
      Integer bitRate, String size, boolean verbose, boolean bugreport, String argsExpected) {
    ScreenRecordArgs.Builder builder =
        ScreenRecordArgs.builder("output")
            .setBitRate(Optional.fromNullable(bitRate))
            .setVerbose(verbose)
            .setBugreport(bugreport)
            .setSize(Optional.fromNullable(size));
    assertThat(builder.build().toShellCmd())
        .isEqualTo(String.format("%s %s output", ScreenRecordArgs.COMMAND_NAME, argsExpected));
  }

  @Test
  public void testBuildArgs_minBitRate() {
    assertThrows(
        IllegalStateException.class,
        () -> ScreenRecordArgs.builder("output").setBitRate(0).build());
  }

  @Test
  public void testBuildArgs_sizeFormat() {
    assertThrows(
        IllegalStateException.class,
        () -> ScreenRecordArgs.builder("output").setSize("100X100").build());
  }
}
