import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import {CONFIG_SERVICE} from '../../../../core/services/config/config_service';

import {Footer} from './footer';

describe('Footer Component', () => {
  let component: Footer;
  let fixture: ComponentFixture<Footer>;
  const mockConfigService = jasmine.createSpyObj('CONFIG_SERVICE', [
    'checkDeviceWritePermission',
    'checkHostWritePermission',
  ]);

  beforeEach(async () => {
    mockConfigService.checkDeviceWritePermission.and.returnValue(
        of({hasPermission: true, userName: 'derekchen'}),
    );
    mockConfigService.checkHostWritePermission.and.returnValue(
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

    fixture = TestBed.createComponent(Footer);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
    expect(mockConfigService.checkDeviceWritePermission).toHaveBeenCalled();
  });
});
