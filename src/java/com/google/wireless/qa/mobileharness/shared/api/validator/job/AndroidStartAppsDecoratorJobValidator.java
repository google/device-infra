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

package com.google.wireless.qa.mobileharness.shared.api.validator.job;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/** Job validator for the {@code AndroidStartAppsDecorator}. */
public class AndroidStartAppsDecoratorJobValidator implements JobValidator {
  public static final String PARAM_START_APPS = "start_apps";

  @Override
  public List<String> validate(JobInfo job) {
    List<String> errors = new ArrayList<>();
    String inputParam = job.params().get(PARAM_START_APPS);
    // Parses intents.
    Gson gson = new Gson();
    try {
      Type listType = new TypeToken<List<String>>() {}.getType();
      var unused = gson.fromJson(inputParam, listType);
    } catch (JsonSyntaxException e) {
      errors.add(
          String.format(
              "Fail to parse intents: %s. \n"
                  + "%s. \n"
                  + "The input of AndroidStartAppsDecorator must be a legal Gson.\n"
                  + "See https://sites.google.com/site/gson/Home \n",
              inputParam, e.getMessage()));
    }
    return errors;
  }
}
