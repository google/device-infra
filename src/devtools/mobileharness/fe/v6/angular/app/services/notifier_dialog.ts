/**
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {CommonModule} from '@angular/common';
import {Component, Inject} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MAT_DIALOG_DATA, MatDialogModule} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';

/** Data to be displayed in a popup dialog box */
export interface NotifierDialogData {
  title: string;
  message: string;
  className?: string;
  icon?: string;
  rejectText?: string;
  confirmText?: string;
}

/** Component that displays a popup dialog box */
@Component({
  standalone: true,
  selector: 'notifier-dialog',
  templateUrl: './notifier_dialog.ng.html',
  styleUrls: ['./notifier_dialog.css'],
  imports: [
    CommonModule,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
  ],
})
export class NotifierDialog {
  constructor(@Inject(MAT_DIALOG_DATA) readonly data:
                  NotifierDialogData) {}
}
