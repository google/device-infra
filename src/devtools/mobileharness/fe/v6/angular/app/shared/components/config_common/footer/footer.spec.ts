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

    await TestBed.configureTestingModule({
      imports: [
        Footer,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [
        provideRouter([]),
        {provide: CONFIG_SERVICE, useValue: mockConfigService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Footer);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('param', {
      deviceId: 'test_device',
      hostName: '',
      universe: '',
    });
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
    expect(mockConfigService.checkDeviceWritePermission).toHaveBeenCalledWith(
      'test_device',
      '',
    );
  });
  it('should re-run permission check only when id or universe changes', () => {
    mockConfigService.checkDeviceWritePermission.calls.reset();

    // Change to same param
    fixture.componentRef.setInput('param', {
      deviceId: 'test_device',
      hostName: '',
      universe: '',
    });
    fixture.detectChanges();
    expect(mockConfigService.checkDeviceWritePermission).not.toHaveBeenCalled();

    // Change id
    fixture.componentRef.setInput('param', {
      deviceId: 'test_device_2',
      hostName: '',
      universe: '',
    });
    fixture.detectChanges();
    expect(mockConfigService.checkDeviceWritePermission).toHaveBeenCalledWith(
      'test_device_2',
      '',
    );

    mockConfigService.checkDeviceWritePermission.calls.reset();

    // Change universe
    fixture.componentRef.setInput('param', {
      deviceId: 'test_device_2',
      hostName: '',
      universe: 'prod',
    });
    fixture.detectChanges();
    expect(mockConfigService.checkDeviceWritePermission).toHaveBeenCalledWith(
      'test_device_2',
      'prod',
    );
  });

  it('should display custom denied message when permission is denied', () => {
    mockConfigService.checkDeviceWritePermission.and.returnValue(
      of({hasPermission: false, userName: 'user'}),
    );
    fixture.componentRef.setInput('deniedMessage', 'Custom Denied Message');
    fixture.componentRef.setInput('param', {
      deviceId: 'test_device_3',
      hostName: '',
      universe: '',
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const statusEl = compiled.querySelector('.permission-status.failure span');
    expect(statusEl?.textContent?.trim()).toEqual('Custom Denied Message');
  });

  it('should display custom granted message when permission is granted', () => {
    mockConfigService.checkDeviceWritePermission.and.returnValue(
      of({hasPermission: true, userName: 'user'}),
    );
    fixture.componentRef.setInput('grantedMessage', 'Custom Granted Message');
    fixture.componentRef.setInput('param', {
      deviceId: 'test_device_4',
      hostName: '',
      universe: '',
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const statusEl = compiled.querySelector('.permission-status.success span');
    expect(statusEl?.textContent?.trim()).toEqual('Custom Granted Message');
  });

  it('should NOT display custom granted message when permission is denied', () => {
    mockConfigService.checkDeviceWritePermission.and.returnValue(
      of({hasPermission: false, userName: 'user'}),
    );
    fixture.componentRef.setInput('grantedMessage', 'Custom Granted Message');
    fixture.componentRef.setInput('param', {
      deviceId: 'test_device_5',
      hostName: '',
      universe: '',
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const statusEl = compiled.querySelector('.permission-status.failure span');
    expect(statusEl?.textContent?.trim()).toEqual(
      'Current user does not have permission to configure this device.',
    );
  });
});
