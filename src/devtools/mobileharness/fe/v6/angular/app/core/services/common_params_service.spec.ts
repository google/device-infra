import {DOCUMENT} from '@angular/common';
import {TestBed} from '@angular/core/testing';

import {CommonParamsService} from './common_params_service';

interface MockWindow {
  location: {search: string};
}

interface MockDocument {
  defaultView: MockWindow;
}

describe('CommonParamsService', () => {
  let service: CommonParamsService;
  let mockDocument: MockDocument;
  let mockWin: MockWindow;

  beforeEach(() => {
    mockWin = {
      location: {
        search: '',
      },
    };
    mockDocument = {
      defaultView: mockWin,
    };
  });

  it('should be created and return empty common params when no params in URL', () => {
    mockWin.location.search = '';
    TestBed.configureTestingModule({
      providers: [
        CommonParamsService,
        {provide: DOCUMENT, useValue: mockDocument},
      ],
    });
    service = TestBed.inject(CommonParamsService);
    expect(service).toBeTruthy();
    expect(service.getCommonParams()).toEqual({});
    expect(service.hasParam('debug')).toBeFalse();
    expect(service.getParam('debug')).toBeNull();
  });

  it('should capture all active common query parameters from initial URL', () => {
    mockWin.location.search =
      '?debug=true&is_embedded_mode=true&force_host_ready=true&force_device_ready=restart&force_all_ready=true&fleet=ats&f=status:READY';
    TestBed.configureTestingModule({
      providers: [
        CommonParamsService,
        {provide: DOCUMENT, useValue: mockDocument},
      ],
    });
    service = TestBed.inject(CommonParamsService);

    expect(service.getCommonParams()).toEqual({
      'debug': 'true',
      'is_embedded_mode': 'true',
      'force_host_ready': 'true',
      'force_device_ready': 'restart',
      'force_all_ready': 'true',
    });
    // commonParams signal holds the same immutable object
    expect(service.commonParams()).toEqual({
      'debug': 'true',
      'is_embedded_mode': 'true',
      'force_host_ready': 'true',
      'force_device_ready': 'restart',
      'force_all_ready': 'true',
    });
    // Non-common params like fleet and f should NOT be captured
    expect(service.getCommonParams()['fleet']).toBeUndefined();
    expect(service.getCommonParams()['f']).toBeUndefined();
  });

  it('should correctly query individual common params using getParam and hasParam', () => {
    mockWin.location.search = '?debug=true&force_all_ready=true';
    TestBed.configureTestingModule({
      providers: [
        CommonParamsService,
        {provide: DOCUMENT, useValue: mockDocument},
      ],
    });
    service = TestBed.inject(CommonParamsService);

    expect(service.hasParam('debug')).toBeTrue();
    expect(service.getParam('debug')).toBe('true');
    expect(service.hasParam('force_all_ready')).toBeTrue();
    expect(service.getParam('force_all_ready')).toBe('true');
    expect(service.hasParam('is_embedded_mode')).toBeFalse();
    expect(service.getParam('is_embedded_mode')).toBeNull();
    expect(service.isEmbeddedMode()).toBeFalse();
  });

  it('should correctly report isEmbeddedMode based on is_embedded_mode parameter', () => {
    mockWin.location.search = '?is_embedded_mode=true';
    TestBed.configureTestingModule({
      providers: [
        CommonParamsService,
        {provide: DOCUMENT, useValue: mockDocument},
      ],
    });
    service = TestBed.inject(CommonParamsService);
    expect(service.isEmbeddedMode()).toBeTrue();
  });
});
