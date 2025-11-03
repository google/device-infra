import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {DeviceDiscovery} from './device_discovery';

describe('DeviceDiscovery Component', () => {
  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            DeviceDiscovery,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          providers: [provideRouter([])],
        })
        .compileComponents();
  });

  it('should be created', () => {
    const fixture = TestBed.createComponent(DeviceDiscovery);
    const comp = fixture.componentInstance;
    fixture.detectChanges();
    expect(comp).toBeTruthy();
    expect(
        fixture.nativeElement.querySelector('.discovery-title').innerText,
        )
        .toBe('Device Discovery');
  });
});
