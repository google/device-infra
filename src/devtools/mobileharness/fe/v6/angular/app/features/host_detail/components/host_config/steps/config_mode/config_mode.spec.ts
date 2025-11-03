import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {ConfigMode} from './config_mode';

describe('ConfigMode Component', () => {
  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            ConfigMode,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          providers: [provideRouter([])],
        })
        .compileComponents();
  });

  it('should be created', () => {
    const fixture = TestBed.createComponent(ConfigMode);
    const comp = fixture.componentInstance;
    fixture.detectChanges();
    expect(comp).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.mode-title').innerText)
        .toBe(
            'Device Configuration Mode',
        );
  });
});
