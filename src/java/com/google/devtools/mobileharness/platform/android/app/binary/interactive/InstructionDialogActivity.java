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

package com.google.devtools.mobileharness.platform.android.app.binary.interactive;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/** An activity to show the instruction of the test. */
public class InstructionDialogActivity extends Activity {

  private static final String TAG = "InstructionDialog";

  private String title;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setFinishOnTouchOutside(false);
    setContentView(R.layout.instruction_dialog_activity);

    Intent intent = getIntent();
    title = intent.getStringExtra(Constants.TITLE);
    setTitle(title);
    String instruction = intent.getStringExtra(Constants.INSTRUCTION);
    if (instruction != null) {
      TextView instructionView = findViewById(R.id.content);
      instructionView.setText(Html.fromHtml(instruction, Html.FROM_HTML_MODE_COMPACT));
    }

    Button button = findViewById(R.id.ok);
    button.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            finish();
          }
        });
  }

  @Override
  public void onDestroy() {
    Log.i(TAG, String.format("Instruction for %s closed.", title));
    super.onDestroy();
  }
}
