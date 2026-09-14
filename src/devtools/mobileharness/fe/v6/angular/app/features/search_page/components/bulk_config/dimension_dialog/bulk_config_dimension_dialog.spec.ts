import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialogRef} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of} from 'rxjs';

import {
  BatchGetConfigurableDimensionKeysResponse,
  BatchGetDeviceDimensionConfigsResponse,
  DeviceConfigChangeStatus,
  DimensionList,
  GetDimensionValueSuggestionsResponse,
} from '../../../../../core/models/device_config_models';
import {
  CONFIG_SERVICE,
  ConfigService,
} from '../../../../../core/services/config/config_service';
import {BulkConfigDimensionDialogData} from '../../../models';
import {BulkConfigDimensionDialog} from './bulk_config_dimension_dialog';

describe('BulkConfigDimensionDialog', () => {
  let fixture: ComponentFixture<MatTestDialogOpener<BulkConfigDimensionDialog>>;
  let component: BulkConfigDimensionDialog;
  let mockConfigService: jasmine.SpyObj<ConfigService>;
  let mockDialogRef: MatDialogRef<BulkConfigDimensionDialog, unknown>;

  const mockCuratedDialogData: BulkConfigDimensionDialogData = {
    deviceIds: ['device-1', 'device-2', 'device-3'],
    dimension: {
      dimensionName: 'recovery',
      displayName: 'recovery',
      dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
    },
    universe: 'google_1p',
    fleet: 'internal',
  };

  const mockReviewDeviceConfigs: BatchGetDeviceDimensionConfigsResponse = {
    entries: [
      {
        deviceId: 'device-1',
        values: ['true'],
        status:
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
      },
      {
        deviceId: 'device-2',
        values: ['false'],
        status: DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
        readOnlyReason: 'Managed by Config Pusher',
      },
      {
        deviceId: 'device-3',
        values: [],
        status:
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
      },
    ],
  };

  const mockValueSuggestions: GetDimensionValueSuggestionsResponse = {
    candidateValues: [
      {values: ['true'], deviceCount: 5000},
      {values: ['false'], deviceCount: 2000},
      {values: [], deviceCount: 800},
    ],
  };

  const mockCandidateKeysResponse: BatchGetConfigurableDimensionKeysResponse = {
    candidateKeys: [
      {
        dimensionName: 'recovery',
        selectedDeviceCount: 3,
        fleetDeviceCount: 8420,
      },
      {
        dimensionName: 'skip_suw_app',
        selectedDeviceCount: 1,
        fleetDeviceCount: 6150,
      },
      {
        dimensionName: 'pool',
        selectedDeviceCount: 0,
        fleetDeviceCount: 5280,
      },
    ],
    systemDimensionNames: [
      'model',
      'sdk_version',
      'battery_level',
      'status',
      'extra_system_prop',
    ],
  };

  beforeEach(async () => {
    mockConfigService = jasmine.createSpyObj('ConfigService', [
      'batchGetDeviceDimensionConfigs',
      'getDimensionValueSuggestions',
      'batchGetConfigurableDimensionKeys',
      'batchUpdateDeviceDimensionConfigs',
    ]);
    mockConfigService.batchGetDeviceDimensionConfigs.and.returnValue(
      of(mockReviewDeviceConfigs),
    );
    mockConfigService.getDimensionValueSuggestions.and.returnValue(
      of(mockValueSuggestions),
    );
    mockConfigService.batchGetConfigurableDimensionKeys.and.returnValue(
      of(mockCandidateKeysResponse),
    );
    mockConfigService.batchUpdateDeviceDimensionConfigs.and.returnValue(
      of({errors: {}}),
    );

    await TestBed.configureTestingModule({
      imports: [
        BulkConfigDimensionDialog,
        NoopAnimationsModule,
        MatTestDialogOpenerModule,
      ],
      providers: [{provide: CONFIG_SERVICE, useValue: mockConfigService}],
    }).compileComponents();

    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(BulkConfigDimensionDialog, {
        data: mockCuratedDialogData,
      }),
    );
    component = fixture.componentInstance.dialogRef.componentInstance;
    mockDialogRef = fixture.componentInstance.dialogRef;
    spyOn(mockDialogRef, 'close');
    fixture.detectChanges();
  });

  describe('Curated Dimension Flow (2-step)', () => {
    it('initializes on values phase and loads value suggestions without pre-filling target values', () => {
      expect(component.phase()).toBe('values');
      expect(component.isCustom).toBeFalse();
      expect(component.headline()).toBe('Configure recovery');
      expect(component.supportText()).toContain('Step 1 of 2');

      expect(
        mockConfigService.getDimensionValueSuggestions,
      ).toHaveBeenCalledWith(
        jasmine.objectContaining({
          dimensionName: 'recovery',
          dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
          universe: 'google_1p',
          fleet: 'internal',
        }),
      );
      expect(
        mockConfigService.batchGetDeviceDimensionConfigs,
      ).not.toHaveBeenCalled();
      expect(component.candidateValues().length).toBe(3);
      expect(component.targetValues()).toEqual([]);
      expect(component.isStepLoading()).toBeFalse();
    });

    it('does not display current value when there is only one device selected', () => {
      const singleDeviceFixture = TestBed.createComponent(
        MatTestDialogOpener.withComponent(BulkConfigDimensionDialog, {
          data: {
            ...mockCuratedDialogData,
            deviceIds: ['single-device-1'],
          },
        }),
      );
      const singleDeviceComponent =
        singleDeviceFixture.componentInstance.dialogRef.componentInstance;
      singleDeviceFixture.detectChanges();

      expect(singleDeviceComponent.targetValues()).toEqual([]);
      expect(singleDeviceComponent.isStepLoading()).toBeFalse();
      expect(
        mockConfigService.batchGetDeviceDimensionConfigs,
      ).not.toHaveBeenCalled();
    });

    it('handles chip input interactions: enter, comma, backspace, paste, remove, clear', () => {
      component.inputValue.set('chip1');
      const enterEvent = new KeyboardEvent('keydown', {key: 'Enter'});
      spyOn(enterEvent, 'preventDefault');
      component.onInputKeydown(enterEvent);
      expect(component.targetValues()).toEqual(['chip1']);
      expect(component.inputValue()).toBe('');

      // Comma
      const inputEl = document.createElement('input');
      inputEl.value = 'chip2';
      component.inputValue.set('chip2');
      const commaEvent = {
        key: ',',
        target: inputEl,
        preventDefault: jasmine.createSpy('preventDefault'),
      } as unknown as KeyboardEvent;
      component.onInputKeydown(commaEvent);
      expect(component.targetValues()).toEqual(['chip1', 'chip2']);

      // Backspace on empty input pops last chip
      inputEl.value = '';
      inputEl.selectionStart = 0;
      inputEl.selectionEnd = 0;
      const backspaceEvent = {
        key: 'Backspace',
        target: inputEl,
        preventDefault: jasmine.createSpy('preventDefault'),
      } as unknown as KeyboardEvent;
      component.onInputKeydown(backspaceEvent);
      expect(component.targetValues()).toEqual(['chip1']);
      expect(component.inputValue()).toBe('chip2');

      // Paste
      const pasteEvent = {
        preventDefault: jasmine.createSpy('preventDefault'),
        clipboardData: {getData: () => 'valA, valB\nvalC'},
      } as unknown as ClipboardEvent;
      component.onInputPaste(pasteEvent);
      expect(component.targetValues()).toEqual([
        'chip1',
        'chip2valA',
        'valB',
        'valC',
      ]);

      // Remove chip
      component.removeChip(0);
      expect(component.targetValues()).toEqual(['chip2valA', 'valB', 'valC']);

      // Clear target
      component.clearTarget();
      expect(component.targetValues()).toEqual([]);

      // Use value set
      component.useValueSet(['optA', 'optB']);
      expect(component.targetValues()).toEqual(['optA', 'optB']);
    });

    it('advances to Step 2 review by querying backend with target values', () => {
      component.targetValues.set(['true']);
      component.goToReview();

      expect(
        mockConfigService.batchGetDeviceDimensionConfigs,
      ).toHaveBeenCalledWith(
        jasmine.objectContaining({
          deviceIds: ['device-1', 'device-2', 'device-3'],
          dimensionName: 'recovery',
          dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
          target: {valueList: {values: ['true']}},
        }),
      );

      expect(component.phase()).toBe('review');
      expect(component.supportText()).toContain(
        'Step 2 of 2 · Review and apply',
      );
      expect(component.reviewSummary().willUpdateRows.length).toBe(1); // device-3
      expect(component.reviewSummary().alreadyMatchCount).toBe(1); // device-1
      expect(component.reviewSummary().readOnlyCount).toBe(1); // device-2
      expect(component.expandedDeviceValues().size).toBe(0);
    });

    it('toggles expansion state for device values in review table', () => {
      expect(component.expandedDeviceValues().has('device-1')).toBeFalse();

      component.toggleExpandDeviceValues('device-1');
      expect(component.expandedDeviceValues().has('device-1')).toBeTrue();

      component.toggleExpandDeviceValues('device-1');
      expect(component.expandedDeviceValues().has('device-1')).toBeFalse();
    });

    it('applies batch update for only WILL_UPDATE devices', () => {
      component.targetValues.set(['true']);
      component.goToReview();
      component.apply();

      expect(
        mockConfigService.batchUpdateDeviceDimensionConfigs,
      ).toHaveBeenCalledWith({
        deviceIds: ['device-3'],
        dimensionName: 'recovery',
        dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
        target: {valueList: {values: ['true']}},
        universe: 'google_1p',
        fleet: 'internal',
      });
      expect(component.applyResult()).toBe('success');
      expect(component.headline()).toBe('recovery updated');

      component.close();
      expect(mockDialogRef.close).toHaveBeenCalledWith({updated: true});
    });

    it('displays error screen when backend returns update errors', () => {
      mockConfigService.batchUpdateDeviceDimensionConfigs.and.returnValue(
        of({
          errors: {
            'device-3': {
              code: 'PERMISSION_DENIED',
              message: 'Locked by system policy',
            },
          },
        }),
      );

      component.targetValues.set(['true']);
      component.goToReview();
      component.apply();

      expect(component.applyResult()).toBe('errors');
      expect(component.headline()).toBe('1 device could not be updated');
      expect(component.applyErrors()).toEqual([
        {deviceId: 'device-3', reason: 'Locked by system policy'},
      ]);

      component.close();
      expect(mockDialogRef.close).toHaveBeenCalled();
    });

    it('clears target values and sets targetDimensionValues to clearValues', () => {
      component.targetValues.set(['true']);
      expect(component.targetDimensionValues()).toEqual({
        valueList: {values: ['true']},
      });

      component.clearTarget();
      expect(component.targetValues()).toEqual([]);
      expect(component.targetDimensionValues()).toEqual({
        clearValues: {},
      });
    });

    it('computes applySuccessCount when some updates fail', () => {
      mockConfigService.batchGetDeviceDimensionConfigs.and.returnValue(
        of({
          entries: [
            {
              deviceId: 'device-1',
              values: [],
              status:
                DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
            },
            {
              deviceId: 'device-2',
              values: [],
              status:
                DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
            },
            {
              deviceId: 'device-3',
              values: [],
              status:
                DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
            },
          ],
        }),
      );

      mockConfigService.batchUpdateDeviceDimensionConfigs.and.returnValue(
        of({
          errors: {
            'device-3': {
              code: 'PERMISSION_DENIED',
              message: 'Locked by system policy',
            },
          },
        }),
      );

      component.targetValues.set(['test_val']);
      component.goToReview();
      expect(component.reviewSummary().willUpdateCount).toBe(3);

      component.apply();

      expect(component.applyResult()).toBe('errors');
      expect(component.applyErrors().length).toBe(1);
      expect(component.applySuccessCount()).toBe(2);

      component.close();
      expect(mockDialogRef.close).toHaveBeenCalledWith({updated: true});
    });

    it('returns to values phase on goBackToValues', () => {
      component.goToReview();
      expect(component.phase()).toBe('review');
      component.goBackToValues();
      expect(component.phase()).toBe('values');
    });

    it('displays nothing-to-update message when changedCount is 0', () => {
      mockConfigService.batchGetDeviceDimensionConfigs.and.returnValue(
        of({
          entries: [
            {
              deviceId: 'device-1',
              values: ['true'],
              status:
                DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
            },
            {
              deviceId: 'device-2',
              values: ['true'],
              status:
                DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
            },
            {
              deviceId: 'device-3',
              values: ['true'],
              status:
                DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
            },
          ],
        }),
      );

      component.targetValues.set(['true']);
      component.goToReview();

      expect(component.reviewSummary().willUpdateCount).toBe(0);
      expect(component.nothingToUpdateMessage()).toContain(
        'already have this recovery value',
      );
    });
  });

  describe('Custom Dimension Flow (3-step)', () => {
    let customFixture: ComponentFixture<
      MatTestDialogOpener<BulkConfigDimensionDialog>
    >;
    let customComponent: BulkConfigDimensionDialog;

    const mockCustomDialogData: BulkConfigDimensionDialogData = {
      deviceIds: ['device-1', 'device-2'],
      dimensionList: DimensionList.DIMENSION_LIST_REQUIRED,
      universe: 'google_1p',
      fleet: 'internal',
    };

    beforeEach(async () => {
      customFixture = TestBed.createComponent(
        MatTestDialogOpener.withComponent(BulkConfigDimensionDialog, {
          data: mockCustomDialogData,
        }),
      );
      customComponent =
        customFixture.componentInstance.dialogRef.componentInstance;
      customFixture.detectChanges();
    });

    it('initializes on key phase and fetches candidate dimension keys', () => {
      expect(customComponent.isCustom).toBeTrue();
      expect(customComponent.phase()).toBe('key');
      expect(customComponent.headline()).toBe('Configure Required Dimension');
      expect(customComponent.supportText()).toContain('Step 1 of 3');

      expect(
        mockConfigService.batchGetConfigurableDimensionKeys,
      ).toHaveBeenCalledWith(
        jasmine.objectContaining({
          deviceIds: ['device-1', 'device-2'],
          dimensionList: DimensionList.DIMENSION_LIST_REQUIRED,
        }),
      );
      expect(customComponent.candidateKeys().length).toBe(3);
    });

    it('validates custom key input against regex and system dimension blocklist', () => {
      // Empty input
      customComponent.onCustomKeyInput('');
      expect(customComponent.customKeyError()).toBe('');
      expect(customComponent.canProceedFromKeyStep()).toBeFalse();

      // System key
      customComponent.onCustomKeyInput('model');
      expect(customComponent.customKeyError()).toContain(
        'automatically detected by OmniLab',
      );
      expect(customComponent.canProceedFromKeyStep()).toBeFalse();

      // Invalid character
      customComponent.onCustomKeyInput('invalid key with space');
      expect(customComponent.customKeyError()).toContain(
        'lowercase letters, numbers, underscores',
      );
      expect(customComponent.canProceedFromKeyStep()).toBeFalse();

      // Valid new key
      customComponent.onCustomKeyInput('my_new_custom_dim');
      expect(customComponent.customKeyError()).toBe('');
      expect(customComponent.canProceedFromKeyStep()).toBeTrue();
      expect(customComponent.isNewKey()).toBeTrue();
    });

    it('advances from key phase to values phase and allows going back', () => {
      customComponent.onCustomKeyInput('skip_suw_app');
      expect(customComponent.isNewKey()).toBeFalse();
      customComponent.goFromKeyToValues();

      expect(customComponent.phase()).toBe('values');
      expect(customComponent.dimensionKey()).toBe('skip_suw_app');
      expect(customComponent.headline()).toBe(
        'Configure Required Dimension: skip_suw_app',
      );
      expect(customComponent.supportText()).toContain('Step 2 of 3');

      // Go back to Key Step
      customComponent.goBackToKey();
      expect(customComponent.phase()).toBe('key');
      expect(customComponent.supportText()).toContain('Step 1 of 3');
    });

    it('advances from values phase to review phase in custom flow', () => {
      customComponent.onCustomKeyInput('skip_suw_app');
      customComponent.goFromKeyToValues();
      customComponent.targetValues.set(['true']);

      customComponent.goToReview();

      expect(customComponent.phase()).toBe('review');
      expect(customComponent.supportText()).toContain(
        'Step 3 of 3 · Review and apply',
      );

      customComponent.goBackToValues();
      expect(customComponent.phase()).toBe('values');
    });

    it('allows selecting an existing candidate key via pickKey', () => {
      customComponent.pickKey('pool');
      expect(customComponent.customKey()).toBe('pool');
      expect(customComponent.canProceedFromKeyStep()).toBeTrue();
    });
  });
});
