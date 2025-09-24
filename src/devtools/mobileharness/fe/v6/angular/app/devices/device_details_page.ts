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

import {Component, OnInit} from '@angular/core';
import {Title} from '@angular/platform-browser';
import {ActivatedRoute} from '@angular/router';

/** A component for displaying the details of a device. */
@Component({
  standalone: true,
  selector: 'device-details-page',
  templateUrl: './device_details_page.ng.html',
})
export class DeviceDetailsPage implements OnInit {
  id = '';

  constructor(
      private readonly route: ActivatedRoute, private readonly title: Title) {}

  ngOnInit() {
    this.title.setTitle(`Android Test Station - Device Details`);
    this.route.params.subscribe(params => {
      this.id = params['id'] || '';
    });
  }
}
