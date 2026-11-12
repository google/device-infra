import {ApplicationRef} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialogRef} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of, Subject, throwError} from 'rxjs';

import {
  BatchGetConfigurableDimensionKeysResponse,
  BatchGetDeviceDimensionConfigsResponse,
  BatchUpdateDeviceDimensionConfigsResponse,
  DeviceConfigChangeStatus,
  DimensionList,
  GetDimensionValueSuggestionsResponse,
} from '../../../../../core/models/device_config_models';
import {
  CONFIG_SERVICE,
  ConfigService,
} from '../../../../../core/services/config/config_service';
import {BulkConfigDialogData} from '../../../models';
import {BulkConfigDimensionDialog} from './bulk_config_dimension_dialog';

describe('BulkConfigDimensionDialog', () => {
  let fixture: ComponentFixture<MatTestDialogOpener<BulkConfigDimensionDialog>>;
  let component: BulkConfigDimensionDialog;
  let mockConfigService: jasmine.SpyObj<ConfigService>;
  let mockDialogRef: MatDialogRef<BulkConfigDimensionDialog, unknown>;
  let appRef: ApplicationRef;

  function detectChanges(
    targetFixture: ComponentFixture<
      MatTestDialogOpener<BulkConfigDimensionDialog>
    > = fixture,
  ) {
    targetFixture.detectChanges();
    appRef.tick();
  }

  const mockCuratedDialogData: BulkConfigDialogData = {
    devices: [
      {deviceId: 'device-1', hostName: 'host-1'},
      {deviceId: 'device-2', hostName: 'host-2'},
      {deviceId: 'device-3', hostName: 'host-3'},
    ],
    dimension: {
      dimensionName: 'recovery',
      dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
    },
  };

  const mockReviewDeviceConfigs: BatchGetDeviceDimensionConfigsResponse = {
    entries: [
      {
        deviceId: 'device-1',
        hostName: 'host-1',
        values: ['true'],
        status:
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
      },
      {
        deviceId: 'device-2',
        hostName: 'host-2',
        values: ['false'],
        status: DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
        readOnlyReason: 'Managed by Config Pusher',
      },
      {
        deviceId: 'device-3',
        hostName: 'host-3',
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

    appRef = TestBed.inject(ApplicationRef);
  });

  describe('Curated Dimension Flow (2-step)', () => {
    beforeEach(() => {
      fixture = TestBed.createComponent(
        MatTestDialogOpener.withComponent(BulkConfigDimensionDialog, {
          autoFocus: false,
          data: mockCuratedDialogData,
        }),
      );
      component = fixture.componentInstance.dialogRef.componentInstance;
      mockDialogRef = fixture.componentInstance.dialogRef;
      spyOn(mockDialogRef, 'close');
      detectChanges();
    });
    it('initializes on values phase and loads value suggestions without pre-filling target values', () => {
      expect(component.wizard.phase()).toBe('values');
      expect(component.isCustom).toBeFalse();
      expect(component.headline()).toBe(
        'Configure Supported Dimension: recovery',
      );
      expect(component.supportText()).toContain('Step 1 of 2');

      expect(
        mockConfigService.getDimensionValueSuggestions,
      ).toHaveBeenCalledWith({
        dimensionName: 'recovery',
        dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
      });
      expect(
        mockConfigService.batchGetDeviceDimensionConfigs,
      ).not.toHaveBeenCalled();
      expect(component.candidateValues().length).toBe(2);
      expect(component.targetValues()).toEqual([]);
      expect(component.isClearDimension()).toBeFalse();
      expect(component.canProceedFromValuesStep()).toBeFalse();
      expect(component.wizard.viewMode()).toBe('values');
      expect(document.activeElement).toBe(
        component.chipInputField()!.nativeElement,
      );
    });

    it('keeps values step interactive and shows inline loading/error while getDimensionValueSuggestions is pending or fails', () => {
      const suggestions$ = new Subject<GetDimensionValueSuggestionsResponse>();
      mockConfigService.getDimensionValueSuggestions.and.returnValue(
        suggestions$,
      );

      component.loadValuesStepData();
      detectChanges();

      expect(component.wizard.viewMode()).toBe('values');
      expect(component.valuesTask.isLoading()).toBeTrue();
      expect(component.candidateValues()).toEqual([]);
      expect(document.body.textContent).toContain(
        'Loading suggested values from fleet…',
      );
      expect(document.body.textContent).toContain('Not set');

      // User can immediately select "Not set" or type values while suggestions are still loading
      component.clearTargetDimension();
      expect(component.canProceedFromValuesStep()).toBeTrue();

      suggestions$.error(new Error('Value suggestions RPC timed out'));
      detectChanges();

      expect(component.wizard.viewMode()).toBe('values');
      expect(component.valuesTask.isLoading()).toBeFalse();
      expect(component.valuesTask.error()).toBe(
        'Value suggestions RPC timed out',
      );
      expect(document.body.textContent).toContain(
        'Value suggestions RPC timed out',
      );
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

      // Clear typed chips (stays in value input mode, disables Next)
      component.useValueSet([]);
      expect(component.targetValues()).toEqual([]);
      expect(component.isClearDimension()).toBeFalse();
      expect(component.canProceedFromValuesStep()).toBeFalse();

      // Use value set
      component.chipInputField()!.nativeElement.blur();
      component.useValueSet(['optA', 'optB']);
      expect(component.targetValues()).toEqual(['optA', 'optB']);
      expect(component.isClearDimension()).toBeFalse();
      expect(component.canProceedFromValuesStep()).toBeTrue();
      expect(component.chipPlaceholder()).toBe('Add another value');
      expect(document.activeElement).toBe(
        component.chipInputField()!.nativeElement,
      );
    });

    it('focuses chip input when clicking chips container except on remove button', () => {
      component.targetValues.set(['chip1']);
      detectChanges();

      const inputNative = component.chipInputField()!.nativeElement;
      const focusSpy = spyOn(inputNative, 'focus');

      const overlayRoot = document.body;
      const removeBtn = overlayRoot.querySelector(
        '.bd-chip-remove',
      ) as HTMLButtonElement;
      removeBtn.click();
      expect(focusSpy).not.toHaveBeenCalled();
      expect(component.targetValues()).toEqual([]);

      const chipsContainer = overlayRoot.querySelector(
        '.bd-chips',
      ) as HTMLElement;
      chipsContainer.click();
      expect(focusSpy).toHaveBeenCalledTimes(1);
    });

    it('advances to Step 2 review by querying backend with target values', () => {
      component.targetValues.set(['true']);
      component.goToReview();

      expect(
        mockConfigService.batchGetDeviceDimensionConfigs,
      ).toHaveBeenCalledWith(
        jasmine.objectContaining({
          devices: mockCuratedDialogData.devices,
          dimensionName: 'recovery',
          dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
          target: {valueList: {values: ['true']}},
        }),
      );

      expect(component.wizard.phase()).toBe('review');
      expect(component.supportText()).toContain(
        'Step 2 of 2 · Review and apply',
      );
      expect(component.wizard.reviewSummary().willUpdateCount).toBe(1); // device-3
      expect(component.wizard.reviewSummary().alreadyMatchCount).toBe(1); // device-1
      expect(component.wizard.reviewSummary().readOnlyCount).toBe(1); // device-2
      expect(component.expandedDeviceValues().size).toBe(0);
    });

    it('toggles expansion state for device values in review table using composite key and renders hostName', () => {
      mockConfigService.batchGetDeviceDimensionConfigs.and.returnValue(
        of({
          entries: [
            {
              deviceId: '000000000000',
              hostName: 'mh-lab-01.google.com',
              values: ['v1', 'v2', 'v3'],
              status:
                DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
              readOnlyReason: 'Unrecognizable or invalid device ID',
            },
            {
              deviceId: '000000000000',
              hostName: 'mh-lab-02.google.com',
              values: ['v4', 'v5', 'v6'],
              status:
                DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
              readOnlyReason: 'Unrecognizable or invalid device ID',
            },
          ],
        }),
      );

      component.targetValues.set(['true']);
      component.goToReview();
      detectChanges();

      const target1 = {
        deviceId: '000000000000',
        hostName: 'mh-lab-01.google.com',
      };
      expect(component.reviewRows()[0].isExpanded).toBeFalse();
      expect(component.reviewRows()[1].isExpanded).toBeFalse();

      const tableRows = document.body.querySelectorAll(
        '.bw-review-table tbody tr',
      );
      expect(tableRows.length).toBe(2);
      expect(tableRows[0].textContent).toContain('000000000000');
      expect(tableRows[0].textContent).toContain('mh-lab-01.google.com');
      expect(tableRows[1].textContent).toContain('000000000000');
      expect(tableRows[1].textContent).toContain('mh-lab-02.google.com');

      component.toggleExpandDeviceValues(target1);
      expect(component.reviewRows()[0].isExpanded).toBeTrue();
      expect(component.reviewRows()[1].isExpanded).toBeFalse();

      component.toggleExpandDeviceValues(target1);
      expect(component.reviewRows()[0].isExpanded).toBeFalse();
    });

    it('applies batch update for only WILL_UPDATE devices and shows applying/success states', () => {
      const applySubject =
        new Subject<BatchUpdateDeviceDimensionConfigsResponse>();
      mockConfigService.batchUpdateDeviceDimensionConfigs.and.returnValue(
        applySubject,
      );

      component.targetValues.set(['true']);
      component.goToReview();
      component.apply();

      expect(component.wizard.viewMode()).toBe('applying');
      expect(component.headline()).toBe(
        'Applying Supported Dimension: recovery',
      );
      expect(component.supportText()).toBe('');

      applySubject.next({errors: {}});
      applySubject.complete();

      expect(
        mockConfigService.batchUpdateDeviceDimensionConfigs,
      ).toHaveBeenCalledWith({
        devices: [{deviceId: 'device-3', hostName: 'host-3'}],
        dimensionName: 'recovery',
        dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
        target: {valueList: {values: ['true']}},
      });
      expect(component.wizard.viewMode()).toBe('success');
      expect(component.headline()).toBe(
        'Supported Dimension: recovery updated',
      );
      detectChanges();
      expect(
        document.body.querySelector('.bw-success-support')?.textContent,
      ).toBe('1 device already matched; 1 device read-only.');
      const successCard = document.body.querySelector('.bw-success-card');
      expect(successCard?.querySelector('.bw-new-label')?.textContent).toBe(
        'Supported Dimension: recovery',
      );
      expect(successCard?.textContent).toContain('true');

      component.wizard.close();
      expect(mockDialogRef.close).toHaveBeenCalledWith({updated: true});

      // Verify all-updated branch omits redundant .bw-success-support and renders clear card detail
      component.clearTargetDimension();
      component.wizard.loadReview(
        () =>
          of({
            entries: [
              {
                deviceId: 'device-3',
                values: ['false'],
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
              },
            ],
          }),
        '',
      );
      mockConfigService.batchUpdateDeviceDimensionConfigs.and.returnValue(
        of({errors: {}}),
      );
      component.apply();
      detectChanges();
      expect(document.body.querySelector('.bw-success-support')).toBeNull();
      const clearSuccessCard = document.body.querySelector('.bw-success-card');
      expect(
        clearSuccessCard?.querySelector('.bw-new-label')?.textContent,
      ).toBe('Supported Dimension: recovery');
      expect(clearSuccessCard?.textContent).toContain('Not set');
      expect(clearSuccessCard?.textContent).toContain(
        'Removed from updated devices',
      );
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

      expect(component.wizard.viewMode()).toBe('errors');
      expect(component.headline()).toBe(
        'Configure Supported Dimension: recovery',
      );
      expect(component.supportText()).toBe('Apply completed with errors');
      expect(component.wizard.applyErrors()).toEqual([
        {deviceId: 'device-3', reason: 'Locked by system policy'},
      ]);

      component.wizard.close();
      expect(mockDialogRef.close).toHaveBeenCalled();
    });

    it('keeps user on review step and displays inline error banner when apply RPC fails', () => {
      mockConfigService.batchUpdateDeviceDimensionConfigs.and.returnValue(
        throwError(() => ({
          error: {error: {message: 'Deadline exceeded calling LabServer'}},
        })),
      );

      component.targetValues.set(['true']);
      component.goToReview();
      component.apply();
      detectChanges();

      expect(component.wizard.viewMode()).toBe('review');
      expect(component.wizard.applyRpcError()).toBe(
        'Deadline exceeded calling LabServer',
      );
      const bannerEl = document.body.querySelector('.bw-apply-error-banner');
      expect(bannerEl?.textContent).toContain(
        'Failed to apply Supported Dimension: recovery update',
      );
      expect(bannerEl?.textContent).toContain(
        'Deadline exceeded calling LabServer',
      );
      const primaryBtn = document.body.querySelector('.dialog-button-primary');
      expect(primaryBtn?.textContent?.trim()).toBe('Retry Apply');
    });

    it('switches between value input mode and Not set clear-banner mode', () => {
      component.inputValue.set(' , "" ');
      expect(component.canProceedFromValuesStep()).toBeFalse();

      component.inputValue.set('uncommitted_token');
      expect(component.canProceedFromValuesStep()).toBeTrue();

      component.useValueSet(['true']);
      detectChanges();
      expect(component.isClearDimension()).toBeFalse();
      expect(component.suggestionRows()[0].isActive).toBeTrue();
      expect(component.targetDimensionValues()).toEqual({
        valueList: {values: ['true']},
      });
      expect(document.body.querySelector('.bd-chips')).not.toBeNull();
      expect(document.body.querySelector('.bw-clear-banner')).toBeNull();

      component.inputValue.set('uncommitted');
      component.clearTargetDimension();
      detectChanges();
      expect(component.isClearDimension()).toBeTrue();
      expect(component.suggestionRows()[0].isActive).toBeFalse();
      expect(component.targetValues()).toEqual([]);
      expect(component.inputValue()).toBe('');
      expect(component.canProceedFromValuesStep()).toBeTrue();
      expect(component.targetDimensionValues()).toEqual({
        clearValues: {},
      });
      expect(document.body.querySelector('.bd-chips')).toBeNull();
      const clearBanner = document.body.querySelector('.bw-clear-banner');
      expect(clearBanner).not.toBeNull();
      expect(clearBanner?.textContent).toContain('Not set');
      expect(clearBanner?.textContent).toContain(
        'Removes Supported Dimension: recovery from the selected devices',
      );

      const setValuesBtn = clearBanner?.querySelector(
        '.bw-text-btn',
      ) as HTMLButtonElement;
      expect(setValuesBtn.textContent?.trim()).toBe('Set values instead');
      setValuesBtn.click();
      detectChanges();

      expect(component.isClearDimension()).toBeFalse();
      expect(component.canProceedFromValuesStep()).toBeFalse();
      expect(document.body.querySelector('.bw-clear-banner')).toBeNull();
      expect(document.body.querySelector('.bd-chips')).not.toBeNull();
    });

    it('closes with {updated: true} when some updates succeed and some fail', () => {
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
      expect(component.wizard.reviewSummary().willUpdateCount).toBe(3);

      component.apply();

      expect(component.wizard.viewMode()).toBe('errors');
      expect(component.wizard.applyErrors().length).toBe(1);

      component.wizard.close();
      expect(mockDialogRef.close).toHaveBeenCalledWith({updated: true});
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

      expect(component.wizard.reviewSummary().willUpdateCount).toBe(0);
      expect(component.nothingToUpdateMessage()).toContain(
        'already have this Supported Dimension: recovery value',
      );

      component.clearTargetDimension();
      expect(component.nothingToUpdateMessage()).toContain(
        'already have Supported Dimension: recovery unset',
      );
    });
  });

  describe('Custom Dimension Flow (3-step)', () => {
    let customFixture: ComponentFixture<
      MatTestDialogOpener<BulkConfigDimensionDialog>
    >;
    let customComponent: BulkConfigDimensionDialog;

    const mockCustomDialogData: BulkConfigDialogData = {
      devices: [
        {deviceId: 'device-1', hostName: 'host-1'},
        {deviceId: 'device-2', hostName: 'host-2'},
      ],
      dimensionList: DimensionList.DIMENSION_LIST_REQUIRED,
    };

    beforeEach(() => {
      customFixture = TestBed.createComponent(
        MatTestDialogOpener.withComponent(BulkConfigDimensionDialog, {
          autoFocus: false,
          data: mockCustomDialogData,
        }),
      );
      customComponent =
        customFixture.componentInstance.dialogRef.componentInstance;
      detectChanges(customFixture);
    });

    it('initializes on key phase and fetches candidate dimension keys', () => {
      expect(customComponent.isCustom).toBeTrue();
      expect(customComponent.wizard.phase()).toBe('key');
      expect(customComponent.headline()).toBe('Configure Required Dimension');
      expect(customComponent.supportText()).toContain('Step 1 of 3');

      expect(
        mockConfigService.batchGetConfigurableDimensionKeys,
      ).toHaveBeenCalledWith(
        jasmine.objectContaining({
          devices: mockCustomDialogData.devices,
          dimensionList: DimensionList.DIMENSION_LIST_REQUIRED,
        }),
      );
      expect(customComponent.candidateKeys().length).toBe(3);
      expect(document.activeElement).toBe(
        customComponent.customKeyInputField()!.nativeElement,
      );
    });

    it('validates custom key input against regex and system dimension blocklist', () => {
      // Empty input
      customComponent.onCustomKeyInput('');
      expect(customComponent.customKeyError()).toBe('');
      expect(customComponent.canProceedFromKeyStep()).toBeFalse();
      expect(customComponent.isNewKey()).toBeFalse();

      // System key
      customComponent.onCustomKeyInput('model');
      expect(customComponent.customKeyError()).toContain(
        'automatically detected by OmniLab',
      );
      expect(customComponent.canProceedFromKeyStep()).toBeFalse();
      expect(customComponent.isNewKey()).toBeFalse();

      // Invalid character
      customComponent.onCustomKeyInput('invalid key with space');
      expect(customComponent.customKeyError()).toContain(
        'lowercase letters, numbers, underscores',
      );
      expect(customComponent.canProceedFromKeyStep()).toBeFalse();
      expect(customComponent.isNewKey()).toBeFalse();

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

      expect(customComponent.wizard.phase()).toBe('values');
      expect(customComponent.dimensionKey()).toBe('skip_suw_app');
      expect(customComponent.headline()).toBe(
        'Configure Required Dimension: skip_suw_app',
      );
      expect(customComponent.supportText()).toContain('Step 2 of 3');

      // Go back to Key Step
      customComponent.goBackToKey();
      expect(customComponent.wizard.phase()).toBe('key');
      expect(customComponent.supportText()).toContain('Step 1 of 3');
    });

    it('advances from values phase to review phase in custom flow', () => {
      customComponent.onCustomKeyInput('skip_suw_app');
      customComponent.goFromKeyToValues();
      customComponent.targetValues.set(['true']);

      customComponent.goToReview();

      expect(customComponent.wizard.phase()).toBe('review');
      expect(customComponent.supportText()).toContain(
        'Step 3 of 3 · Review and apply',
      );

      customComponent.wizard.setPhase('values');
      expect(customComponent.wizard.phase()).toBe('values');
    });

    it('allows selecting an existing candidate key via pickKey', () => {
      customComponent.customKeyInputField()!.nativeElement.blur();
      customComponent.pickKey('pool');
      expect(customComponent.customKey()).toBe('pool');
      expect(customComponent.filteredCandidateKeys().length).toBe(1);
      expect(customComponent.filteredCandidateKeys()[0].isActive).toBeTrue();
      expect(customComponent.canProceedFromKeyStep()).toBeTrue();
      expect(document.activeElement).toBe(
        customComponent.customKeyInputField()!.nativeElement,
      );
    });

    it('keeps key step interactive while batchGetConfigurableDimensionKeys is loading or fails, and suppresses isNewKey until loaded', () => {
      const keys$ = new Subject<BatchGetConfigurableDimensionKeysResponse>();
      mockConfigService.batchGetConfigurableDimensionKeys.and.returnValue(
        keys$,
      );

      customComponent.loadConfigurableDimensionKeys();
      customComponent.onCustomKeyInput('brand_new_key');
      detectChanges(customFixture);

      expect(customComponent.wizard.viewMode()).toBe('key');
      expect(customComponent.keysTask.isLoading()).toBeTrue();
      expect(customComponent.isNewKey()).toBeFalse();
      expect(customComponent.canProceedFromKeyStep()).toBeTrue();
      expect(document.body.textContent).toContain(
        'Loading existing dimensions from fleet…',
      );

      keys$.error(new Error('Fleet scan deadline exceeded'));
      detectChanges(customFixture);

      expect(customComponent.wizard.viewMode()).toBe('key');
      expect(customComponent.keysTask.error()).toBe(
        'Fleet scan deadline exceeded',
      );
      expect(customComponent.isNewKey()).toBeFalse();
      expect(customComponent.canProceedFromKeyStep()).toBeTrue();
    });

    it('reuses loaded value suggestions when navigating back to key step and forward with the same key', () => {
      mockConfigService.getDimensionValueSuggestions.calls.reset();

      customComponent.onCustomKeyInput('pool');
      customComponent.goFromKeyToValues();
      expect(
        mockConfigService.getDimensionValueSuggestions,
      ).toHaveBeenCalledTimes(1);

      customComponent.useValueSet(['pool_v1']);
      customComponent.goBackToKey();
      expect(customComponent.wizard.phase()).toBe('key');

      // Advancing again with the same key reuses cached suggestions and preserves targetValues
      customComponent.goFromKeyToValues();
      expect(customComponent.wizard.phase()).toBe('values');
      expect(customComponent.targetValues()).toEqual(['pool_v1']);
      expect(
        mockConfigService.getDimensionValueSuggestions,
      ).toHaveBeenCalledTimes(1);

      // Changing the key resets targetValues and fetches suggestions for the new key
      customComponent.goBackToKey();
      customComponent.onCustomKeyInput('skip_suw_app');
      customComponent.goFromKeyToValues();
      expect(customComponent.targetValues()).toEqual([]);
      expect(
        mockConfigService.getDimensionValueSuggestions,
      ).toHaveBeenCalledTimes(2);
    });

    it('formats Supported Dimension headline and displayName when dimensionList is DIMENSION_LIST_SUPPORTED', () => {
      const supportedFixture = TestBed.createComponent(
        MatTestDialogOpener.withComponent(BulkConfigDimensionDialog, {
          autoFocus: false,
          data: {
            devices: mockCustomDialogData.devices,
            dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
          },
        }),
      );
      const supportedComponent =
        supportedFixture.componentInstance.dialogRef.componentInstance;
      detectChanges(supportedFixture);

      expect(supportedComponent.isRequired).toBeFalse();
      expect(supportedComponent.headline()).toBe(
        'Configure Supported Dimension',
      );
      supportedComponent.onCustomKeyInput('custom_sup_key');
      supportedComponent.goFromKeyToValues();
      expect(supportedComponent.displayName()).toBe(
        'Supported Dimension: custom_sup_key',
      );
      expect(supportedComponent.headline()).toBe(
        'Configure Supported Dimension: custom_sup_key',
      );
    });
  });
});
