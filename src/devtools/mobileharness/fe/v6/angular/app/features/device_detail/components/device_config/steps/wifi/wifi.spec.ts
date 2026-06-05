import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {CONFIG_SERVICE} from '../../../../../../core/services/config/config_service';
import {FakeConfigService} from '../../../../../../core/services/config/fake_config_service';
import {SCENARIO_IN_SERVICE_IDLE} from '../../../../../../core/services/mock_data/devices/01_in_service_idle';

import {Wifi} from './wifi';

describe('Wifi Component', () => {
  let fixture: ComponentFixture<Wifi>;
  let component: Wifi;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        Wifi,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [
        provideRouter([]),
        {provide: CONFIG_SERVICE, useClass: FakeConfigService},
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Wifi);
    component = fixture.componentInstance;
    component.wifi = SCENARIO_IN_SERVICE_IDLE.config!.wifi!;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  describe('Editability', () => {
    it('should enable inputs when editable is true', async () => {
      fixture.componentRef.setInput('uiStatus', {
        visible: true,
        editability: {editable: true},
      });
      component.wifi = {
        type: 'custom',
        ssid: 'test-ssid',
        psk: 'test-psk',
        scanSsid: false,
      };
      component.wifiEnabled.set(true);
      fixture.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const ssidInput = fixture.nativeElement.querySelector(
        '#wifi-ssid',
      ) as HTMLInputElement;
      expect(ssidInput).toBeTruthy();
      expect(ssidInput.disabled).toBeFalse();
    });

    it('should disable inputs when editable is false', async () => {
      fixture.componentRef.setInput('uiStatus', {
        visible: true,
        editability: {editable: false},
      });
      component.wifi = {
        type: 'custom',
        ssid: 'test-ssid',
        psk: 'test-psk',
        scanSsid: false,
      };
      component.wifiEnabled.set(true);
      fixture.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const ssidInput = fixture.nativeElement.querySelector(
        '#wifi-ssid',
      ) as HTMLInputElement;
      expect(ssidInput).toBeTruthy();
      expect(ssidInput.disabled).toBeTrue();
    });

    it('should disable inputs when editable is undefined (omitted)', async () => {
      fixture.componentRef.setInput('uiStatus', {
        visible: true,
        editability: {}, // editable is undefined
      });
      component.wifi = {
        type: 'custom',
        ssid: 'test-ssid',
        psk: 'test-psk',
        scanSsid: false,
      };
      component.wifiEnabled.set(true);
      fixture.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const ssidInput = fixture.nativeElement.querySelector(
        '#wifi-ssid',
      ) as HTMLInputElement;
      expect(ssidInput).toBeTruthy();
      expect(ssidInput.disabled).toBeTrue();
    });
  });
});
