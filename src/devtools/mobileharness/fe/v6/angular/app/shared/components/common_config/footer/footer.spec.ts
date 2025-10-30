import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import {CONFIG_SERVICE} from '../../../../core/services/config/config_service';

import {Footer} from './footer';

describe('Footer Component', () => {
  const mockConfigService = jasmine.createSpyObj('CONFIG_SERVICE', [
    'checkDeviceWritePermission',
  ]);

  beforeEach(async () => {
    mockConfigService.checkDeviceWritePermission.and.returnValue(
      of({hasPermission: true, userName: 'derekchen'}),
    );

    await TestBed
        .configureTestingModule({
          imports: [
            Footer,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          providers: [
            provideRouter([]),
            {provide: CONFIG_SERVICE, useValue: mockConfigService},
          ],
        })
        .compileComponents();
  });

  it('should be created', () => {
    const fixture = TestBed.createComponent(Footer);
    fixture.detectChanges();

    expect(fixture.componentInstance).toBeTruthy();
    expect(mockConfigService.checkDeviceWritePermission).toHaveBeenCalled();
  });
});
