import {TestBed} from '@angular/core/testing';

import {CommonParamsService} from './common_params_service';
import {Environment} from './environment';
import {FeatureEnablementService} from './feature_enablement_service';

describe('FeatureEnablementService', () => {
  /**
   * Creates the service on top of the given captured common params, for the
   * Google-internal build or the OSS build.
   */
  function createService(
    commonParams: Record<string, string>,
    {isGoogleInternal = false}: {isGoogleInternal?: boolean} = {},
  ): FeatureEnablementService {
    // Allow several services to be created within a single spec.
    TestBed.resetTestingModule();
    const mockCommonParamsService = jasmine.createSpyObj<CommonParamsService>(
      'CommonParamsService',
      ['getParam'],
    );
    mockCommonParamsService.getParam.and.callFake(
      (key: string) => commonParams[key] ?? null,
    );
    const mockEnvironment = jasmine.createSpyObj<Environment>('Environment', [
      'isGoogleInternal',
    ]);
    mockEnvironment.isGoogleInternal.and.returnValue(isGoogleInternal);
    TestBed.configureTestingModule({
      providers: [
        FeatureEnablementService,
        {provide: CommonParamsService, useValue: mockCommonParamsService},
        {provide: Environment, useValue: mockEnvironment},
      ],
    });
    return TestBed.inject(FeatureEnablementService);
  }

  /** Returns the answers of all the feature functions of a service. */
  function featuresOf(service: FeatureEnablementService) {
    return {
      bulkWifi: service.isBulkWifiConfigEnabled(),
      bulkDimension: service.isBulkDimensionConfigEnabled(),
      host: service.isHostConfigEnabled(),
      device: service.isDeviceConfigEnabled(),
    };
  }

  const ALL_ENABLED = {
    bulkWifi: true,
    bulkDimension: true,
    host: true,
    device: true,
  };

  describe('in the Google-internal build', () => {
    it('should turn on every configuration feature without any force flag', () => {
      expect(featuresOf(createService({}, {isGoogleInternal: true}))).toEqual(
        ALL_ENABLED,
      );
    });

    it('should keep every configuration feature on with force_ready=config', () => {
      expect(
        featuresOf(
          createService({'force_ready': 'config'}, {isGoogleInternal: true}),
        ),
      ).toEqual(ALL_ENABLED);
    });

    it('should keep every configuration feature on with the force_all_ready global switch', () => {
      expect(
        featuresOf(
          createService({'force_all_ready': 'true'}, {isGoogleInternal: true}),
        ),
      ).toEqual(ALL_ENABLED);
    });
  });

  describe('in the OSS build', () => {
    it('should keep every configuration feature off without any force flag', () => {
      expect(featuresOf(createService({}))).toEqual({
        bulkWifi: false,
        bulkDimension: false,
        host: false,
        device: false,
      });
      expect(featuresOf(createService({'debug': 'true'}))).toEqual({
        bulkWifi: false,
        bulkDimension: false,
        host: false,
        device: false,
      });
    });

    it('should turn on every configuration feature with force_ready=config', () => {
      expect(featuresOf(createService({'force_ready': 'config'}))).toEqual(
        ALL_ENABLED,
      );
    });

    it('should accept config among other names, ignoring surrounding whitespace and case', () => {
      expect(
        featuresOf(createService({'force_ready': 'search, Config '})),
      ).toEqual(ALL_ENABLED);
    });

    it('should ignore the force_ready names it does not know', () => {
      expect(
        featuresOf(createService({'force_ready': 'search,configs'})),
      ).toEqual({
        bulkWifi: false,
        bulkDimension: false,
        host: false,
        device: false,
      });
    });

    it('should turn on every configuration feature with the force_all_ready global switch', () => {
      expect(featuresOf(createService({'force_all_ready': 'true'}))).toEqual(
        ALL_ENABLED,
      );
    });

    it('should ignore the removed force_bulk_config_ready flag', () => {
      expect(
        featuresOf(createService({'force_bulk_config_ready': 'true'})),
      ).toEqual({
        bulkWifi: false,
        bulkDimension: false,
        host: false,
        device: false,
      });
    });
  });

  describe('force_all_ready', () => {
    it('should treat any value other than empty, 0 or false as active, case-insensitively', () => {
      for (const value of ['1', 'yes', 'TRUE']) {
        expect(featuresOf(createService({'force_all_ready': value}))).toEqual(
          ALL_ENABLED,
        );
      }
    });

    it('should treat empty, 0 and false (case-insensitive) as inactive', () => {
      for (const value of ['', '0', 'false', 'FALSE', 'False']) {
        expect(
          createService({'force_all_ready': value}).isBulkWifiConfigEnabled(),
        ).toBeFalse();
      }
    });
  });
});
