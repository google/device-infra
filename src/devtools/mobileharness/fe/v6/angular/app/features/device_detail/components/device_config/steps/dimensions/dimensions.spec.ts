import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {SCENARIO_IN_SERVICE_IDLE} from '../../../../../../core/services/mock_data/devices/01_in_service_idle';

import {Dimensions} from './dimensions';

describe('Dimensions Component', () => {
  let fixture: ComponentFixture<Dimensions>;
  let component: Dimensions;
  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            Dimensions,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          providers: [provideRouter([])],
        })
        .compileComponents();

    fixture = TestBed.createComponent(Dimensions);
    component = fixture.componentInstance;
    component.dimensions = SCENARIO_IN_SERVICE_IDLE.config!.dimensions;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
