import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {ToggleSwitch} from './toggle_switch';

describe('ToggleSwitch Component', () => {
  let component: ToggleSwitch;
  let fixture: ComponentFixture<ToggleSwitch>;

  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            ToggleSwitch,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          providers: [provideRouter([])],
        })
        .compileComponents();

    fixture = TestBed.createComponent(ToggleSwitch);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
