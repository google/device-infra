import {TestBed} from '@angular/core/testing';
import {SnackBarService} from '../../../shared/services/snackbar_service';
import {DeviceConfigUiStatus} from '../../models/device_config_models';
import {DeviceConfigStateService} from './device_config_state_service';

describe('DeviceConfigStateService', () => {
  let service: DeviceConfigStateService;
  let mockSnackBarService: jasmine.SpyObj<SnackBarService>;

  const mockUiStatus: DeviceConfigUiStatus = {
    permissions: {visible: true, editability: {editable: true}},
    wifi: {visible: true, editability: {editable: true}},
    dimensions: {visible: true, editability: {editable: true}},
    settings: {visible: true, editability: {editable: true}},
  };

  beforeEach(() => {
    mockSnackBarService = jasmine.createSpyObj<SnackBarService>('SnackBarService', [
      'showError',
    ]);

    TestBed.configureTestingModule({
      providers: [
        DeviceConfigStateService,
        {provide: SnackBarService, useValue: mockSnackBarService},
      ],
    });
    service = TestBed.inject(DeviceConfigStateService);
  });

  it('should set and get UI status for correct device ID', () => {
    service.setUiStatus('device-1', mockUiStatus);
    expect(service.getUiStatus('device-1')).toEqual(mockUiStatus);
  });

  it('should throw error and show snackbar when getting UI status for incorrect device ID', () => {
    service.setUiStatus('device-1', mockUiStatus);
    expect(() => service.getUiStatus('device-2')).toThrowError(
      'deviceConfig must be loaded from server first.',
    );
    expect(mockSnackBarService.showError).toHaveBeenCalledWith(
      'Error: deviceConfig must be loaded from server first.',
    );
  });

  it('should throw error and show snackbar when getting UI status when not set', () => {
    expect(() => service.getUiStatus('device-1')).toThrowError(
      'deviceConfig must be loaded from server first.',
    );
    expect(mockSnackBarService.showError).toHaveBeenCalledWith(
      'Error: deviceConfig must be loaded from server first.',
    );
  });

  it('should clear the cached state', () => {
    service.setUiStatus('device-1', mockUiStatus);
    service.clear();
    expect(() => service.getUiStatus('device-1')).toThrowError(
      'deviceConfig must be loaded from server first.',
    );
  });
});
