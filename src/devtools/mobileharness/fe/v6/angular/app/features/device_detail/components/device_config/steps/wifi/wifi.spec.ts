import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {SCENARIO_IN_SERVICE_IDLE} from '../../../../../../core/services/mock_data/devices/01_in_service_idle';

import {Wifi} from './wifi';

describe('Wifi Component', () => {
  let fixture: ComponentFixture<Wifi>;
  let component: Wifi;
  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            Wifi,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          providers: [provideRouter([])],
        })
        .compileComponents();
    fixture = TestBed.createComponent(Wifi);
    component = fixture.componentInstance;
    component.wifi = SCENARIO_IN_SERVICE_IDLE.config!.wifi;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
