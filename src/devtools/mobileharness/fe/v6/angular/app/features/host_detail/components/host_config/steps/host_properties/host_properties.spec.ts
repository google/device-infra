import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {HostProperties} from './host_properties';

describe('HostProperties Component', () => {
  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            HostProperties,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          providers: [provideRouter([])],
        })
        .compileComponents();
  });

  it('should be created', () => {
    const fixture = TestBed.createComponent(HostProperties);
    const comp = fixture.componentInstance;
    fixture.detectChanges();
    expect(comp).toBeTruthy();
    expect(
        fixture.nativeElement.querySelector('.properties-title').innerText,
        )
        .toBe('Host Properties');
  });
});
