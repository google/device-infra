import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {SCENARIO_IN_SERVICE_IDLE} from '../../../../../../core/services/mock_data/devices/01_in_service_idle';

import {Permissions} from './permissions';

describe('Permissions Component', () => {
  let fixture: ComponentFixture<Permissions>;
  let component: Permissions;
  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            Permissions,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          providers: [provideRouter([])],
        })
        .compileComponents();
    fixture = TestBed.createComponent(Permissions);
    component = fixture.componentInstance;
    component.permissions = SCENARIO_IN_SERVICE_IDLE.config!.permissions;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
