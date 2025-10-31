import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, OnInit, Output, signal, SimpleChanges} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {MAT_BUTTON_TOGGLE_DEFAULT_OPTIONS, MatButtonToggleChange, MatButtonToggleModule} from '@angular/material/button-toggle';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatSlideToggleModule} from '@angular/material/slide-toggle';

import type {PartStatus} from '../../../../../../core/models/host_config_models';
import {ToggleSwitch} from '../../../../../../shared/components/toggle_switch/toggle_switch';

/**
 * Component for displaying the Wi-Fi configuration of a device in the device
 * configuration workflow.
 */
@Component({
  selector: 'app-wifi',
  standalone: true,
  templateUrl: './wifi.ng.html',
  styleUrl: './wifi.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatSlideToggleModule,
    MatButtonToggleModule,
    MatInputModule,
    MatSelectModule,
    MatFormFieldModule,
    ToggleSwitch,
  ],
  providers: [
    {
      provide: MAT_BUTTON_TOGGLE_DEFAULT_OPTIONS,
      useValue: {appearance: 'standard'},
    },
  ],
})
export class Wifi implements OnInit, OnChanges {
  @Input() type: 'device'|'host' = 'device';
  @Input() workflow: 'wizard' | 'settings' = 'wizard';
  @Input()
  uiStatus: PartStatus = {
    visible: true,
    editability: {editable: true},
  };

  @Input() wifi = {type: 'none', ssid: 'GoogleGuest', psk: '', scanSsid: false};
  @Output() readonly wifiChange = new EventEmitter<{}>();

  wifiEnabled = signal<boolean>(false);

  PRECONFIGURED_WIFI = ['GoogleGuest', 'GoogleGuestPSK', 'WL-MobileHarness'];

  ngOnInit() {
    this.wifiEnabled.set(this.wifi.type !== 'none');
    console.log('wifi', this.wifi);
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['wifi']) {
      this.wifiEnabled.set(this.wifi.type !== 'none');
    }
  }

  onWifiEnabledChange() {
    this.wifiEnabled.set(!this.wifiEnabled());
    this.wifi.type = this.wifiEnabled() ? 'pre-configured' : 'none';
  }

  onWifiTypeChange(event: MatButtonToggleChange) {
    this.wifi.type = event.value;
  }

  onWifiScanSsidChange(enabled: boolean) {
    this.wifi.scanSsid = enabled;
  }
}
