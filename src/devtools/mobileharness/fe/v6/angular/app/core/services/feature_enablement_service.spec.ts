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

  describe('isBulkConfigEnabled in the Google-internal build', () => {
    it('should be enabled without any force flag', () => {
      expect(
        createService({}, {isGoogleInternal: true}).isBulkConfigEnabled(),
      ).toBeTrue();
    });

    it('should stay enabled when the force flags are false', () => {
      expect(
        createService(
          {'force_bulk_config_ready': 'false', 'force_all_ready': 'false'},
          {isGoogleInternal: true},
        ).isBulkConfigEnabled(),
      ).toBeTrue();
    });
  });

  describe('isBulkConfigEnabled in the OSS build', () => {
    it('should be disabled for end users without any force flag', () => {
      expect(createService({}).isBulkConfigEnabled()).toBeFalse();
      expect(createService({'debug': 'true'}).isBulkConfigEnabled()).toBeFalse();
    });

    it('should be enabled by force_bulk_config_ready', () => {
      expect(
        createService({'force_bulk_config_ready': 'true'}).isBulkConfigEnabled(),
      ).toBeTrue();
    });

    it('should be enabled by the force_all_ready master switch', () => {
      expect(
        createService({'force_all_ready': 'true'}).isBulkConfigEnabled(),
      ).toBeTrue();
    });

    it('should let force_all_ready win over an explicit force_bulk_config_ready=false', () => {
      expect(
        createService({
          'force_all_ready': 'true',
          'force_bulk_config_ready': 'false',
        }).isBulkConfigEnabled(),
      ).toBeTrue();
    });

    it('should treat any value other than false as active, case-insensitively', () => {
      expect(
        createService({'force_bulk_config_ready': '1'}).isBulkConfigEnabled(),
      ).toBeTrue();
      expect(
        createService({'force_bulk_config_ready': 'yes'}).isBulkConfigEnabled(),
      ).toBeTrue();
      expect(
        createService({'force_all_ready': 'TRUE'}).isBulkConfigEnabled(),
      ).toBeTrue();
    });

    it('should treat false (case-insensitive) as inactive', () => {
      expect(
        createService({'force_bulk_config_ready': 'false'}).isBulkConfigEnabled(),
      ).toBeFalse();
      expect(
        createService({'force_bulk_config_ready': 'FALSE'}).isBulkConfigEnabled(),
      ).toBeFalse();
      expect(
        createService({'force_all_ready': 'False'}).isBulkConfigEnabled(),
      ).toBeFalse();
    });
  });
});
