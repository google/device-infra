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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.PhaseSkippableDecorator;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAtsDynamicConfigPusherDecoratorSpec;
import javax.inject.Inject;

/**
 * Decorator to push dynamic config files from config repository. Partially branched from {@code
 * com.android.compatibility.common.tradefed.targetprep.DynamicConfigPusher} from Android codebase.
 */
@DecoratorAnnotation(help = "Decorator to push dynamic config files from config repository.")
public class AndroidAtsDynamicConfigPusherDecorator
    extends PhaseSkippableDecorator<
        AndroidAtsDynamicConfigPusherSetupOnlyDecorator,
        AndroidAtsDynamicConfigPusherTeardownOnlyDecorator>
    implements SpecConfigable<AndroidAtsDynamicConfigPusherDecoratorSpec> {

  @Inject
  AndroidAtsDynamicConfigPusherDecorator(Driver decorated, TestInfo testInfo)
      throws MobileHarnessException {
    super(decorated, testInfo);
  }
}
