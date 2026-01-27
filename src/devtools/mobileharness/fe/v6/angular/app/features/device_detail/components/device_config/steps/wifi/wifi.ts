import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  OnInit,
  Output,
  signal,
  SimpleChanges,
} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {
  MAT_BUTTON_TOGGLE_DEFAULT_OPTIONS,
  MatButtonToggleChange,
  MatButtonToggleModule,
} from '@angular/material/button-toggle';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatSlideToggleModule} from '@angular/material/slide-toggle';
import {MatTooltipModule} from '@angular/material/tooltip';
import {finalize} from 'rxjs/operators';

import {RecommendedWifi} from '../../../../../../core/models/device_config_models';
import type {PartStatus} from '../../../../../../core/models/host_config_models';
import {CONFIG_SERVICE} from '../../../../../../core/services/config/config_service';
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
    MatButtonModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatFormFieldModule,
    MatTooltipModule,
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
  private readonly configService = inject(CONFIG_SERVICE);

  @Input() type: 'device' | 'host' = 'device';
  @Input() workflow: 'wizard' | 'settings' = 'wizard';
  @Input()
  uiStatus: PartStatus = {
    visible: true,
    editability: {editable: true},
  };

  @Input() wifi = {type: 'none', ssid: 'GoogleGuest', psk: '', scanSsid: false};
  @Output() readonly wifiChange = new EventEmitter<{}>();

  wifiEnabled = signal<boolean>(false);
  showRecommendations = signal<boolean>(false);
  recommendationFilter = signal<string>('');
  passwordVisible = signal<boolean>(false);
  recommendedWifi = signal<RecommendedWifi[]>([]);
  isLoading = signal<boolean>(false);

  filteredRecommendations = computed(() => {
    const filter = this.recommendationFilter().toLowerCase();
    return this.recommendedWifi().filter((net) =>
      net.ssid.toLowerCase().includes(filter),
    );
  });

  ngOnInit() {
    this.wifiEnabled.set(this.wifi.type !== 'none');
    this.isLoading.set(true);
    this.configService
      .getRecommendedWifi()
      .pipe(finalize(() => {
          this.isLoading.set(false);
        }),
      )
      .subscribe((recommendations) => {
        this.recommendedWifi.set(recommendations);
      });
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['wifi']) {
      this.wifiEnabled.set(this.wifi.type !== 'none');
    }
  }

  onWifiEnabledChange() {
    this.wifiEnabled.set(!this.wifiEnabled());
    // Default to 'custom' when enabling, as we now use a unified manual input UI
    this.wifi.type = this.wifiEnabled() ? 'custom' : 'none';
  }

  // No longer used in new UI, but kept for compatibility or strict mode if needed
  onWifiTypeChange(event: MatButtonToggleChange) {
    this.wifi.type = event.value;
  }

  onWifiScanSsidChange(enabled: boolean) {
    this.wifi.scanSsid = enabled;
  }

  toggleRecommendations() {
    this.showRecommendations.update((v) => !v);
  }

  applyRecommendation(net: {ssid: string; psk: string}) {
    this.wifi.type = 'custom';
    this.wifi.ssid = net.ssid;
    this.wifi.psk = net.psk;
  }

  togglePasswordVisibility() {
    this.passwordVisible.update((v) => !v);
  }

  clearSsid() {
    this.wifi.ssid = '';
    this.wifi.psk = '';
  }
}
