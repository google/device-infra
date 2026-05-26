import {TestBed} from '@angular/core/testing';
import {SnackBarService} from '../../../shared/services/snackbar_service';
import {HostConfigUiStatus} from '../../models/host_config_models';
import {HostConfigStateService} from './host_config_state_service';

describe('HostConfigStateService', () => {
  let service: HostConfigStateService;
  let mockSnackBarService: jasmine.SpyObj<SnackBarService>;

  beforeEach(() => {
    mockSnackBarService = jasmine.createSpyObj('SnackBarService', [
      'showError',
    ]);

    TestBed.configureTestingModule({
      providers: [
        HostConfigStateService,
        {provide: SnackBarService, useValue: mockSnackBarService},
      ],
    });

    service = TestBed.inject(HostConfigStateService);
    service.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should set and get UI status for same host', () => {
    const status: HostConfigUiStatus = {
      hostAdmins: {visible: true},
      deviceConfigMode: {visible: true},
      deviceConfig: {sectionStatus: {visible: true}, subSections: {}},
      hostProperties: {sectionStatus: {visible: true}},
      deviceDiscovery: {visible: true},
    };

    service.setUiStatus('test-host', status);

    expect(service.getUiStatus('test-host')).toBe(status);
  });

  it('should throw error and show snackbar if host does not match', () => {
    const status: HostConfigUiStatus = {
      hostAdmins: {visible: true},
      deviceConfigMode: {visible: true},
      deviceConfig: {sectionStatus: {visible: true}, subSections: {}},
      hostProperties: {sectionStatus: {visible: true}},
      deviceDiscovery: {visible: true},
    };

    service.setUiStatus('test-host', status);

    expect(() => service.getUiStatus('other-host')).toThrowError(
      'hostConfig must be loaded from server first.',
    );
    expect(mockSnackBarService.showError).toHaveBeenCalledWith(
      'Error: hostConfig must be loaded from server first.',
    );
  });

  it('should throw error and show snackbar if no status set', () => {
    expect(() => service.getUiStatus('test-host')).toThrowError(
      'hostConfig must be loaded from server first.',
    );
    expect(mockSnackBarService.showError).toHaveBeenCalledWith(
      'Error: hostConfig must be loaded from server first.',
    );
  });

  it('should clear status', () => {
    const status: HostConfigUiStatus = {
      hostAdmins: {visible: true},
      deviceConfigMode: {visible: true},
      deviceConfig: {sectionStatus: {visible: true}, subSections: {}},
      hostProperties: {sectionStatus: {visible: true}},
      deviceDiscovery: {visible: true},
    };

    service.setUiStatus('test-host', status);
    service.clear();

    expect(() => service.getUiStatus('test-host')).toThrowError(
      'hostConfig must be loaded from server first.',
    );
  });
});
