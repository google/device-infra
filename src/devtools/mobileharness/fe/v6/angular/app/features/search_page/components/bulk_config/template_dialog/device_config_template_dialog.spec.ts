import {OverlayContainer} from '@angular/cdk/overlay';
import {ApplicationRef} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialogRef} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of} from 'rxjs';

import {
  DeviceConfigChangeStatus,
  DimensionList,
} from '../../../../../core/models/device_config_models';
import {
  CONFIG_SERVICE,
  ConfigService,
} from '../../../../../core/services/config/config_service';
import {
  SEARCH_SERVICE,
  SearchService,
} from '../../../../../core/services/search/search_service';
import {DeviceConfigTemplateDialog} from './device_config_template_dialog';

describe('DeviceConfigTemplateDialog', () => {
  let fixture: ComponentFixture<
    MatTestDialogOpener<DeviceConfigTemplateDialog>
  >;
  let component: DeviceConfigTemplateDialog;
  let mockConfigService: jasmine.SpyObj<ConfigService>;
  let mockSearchService: jasmine.SpyObj<SearchService>;
  let mockDialogRef: MatDialogRef<DeviceConfigTemplateDialog>;
  let overlayElement: HTMLElement;
  let appRef: ApplicationRef;

  function detectChanges() {
    fixture.detectChanges();
    appRef.tick();
  }

  beforeEach(async () => {
    mockConfigService = jasmine.createSpyObj<ConfigService>('ConfigService', [
      'getConfigurableDimensions',
      'getDeviceConfigTemplate',
      'updateDeviceConfigTemplate',
      'batchGetDeviceDimensionConfigs',
      'batchUpdateDeviceDimensionConfigs',
    ]);
    mockSearchService = jasmine.createSpyObj<SearchService>('SearchService', [
      'searchFleet',
    ]);

    mockConfigService.getConfigurableDimensions.and.returnValue(
      of({
        enabled: true,
        dimensions: [
          {
            dimensionName: 'recovery',
            displayName: 'recovery',
            dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
          },
          {
            dimensionName: 'pool',
            displayName: 'pool',
            dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
          },
        ],
        allowCustomDimensions: false,
      }),
    );

    mockConfigService.getDeviceConfigTemplate.and.returnValue(
      of({
        enabled: true,
        deviceConfigTemplate: {
          dimensions: {
            supported: [{name: 'recovery', value: 'true'}],
            required: [],
          },
        },
      }),
    );

    mockConfigService.updateDeviceConfigTemplate.and.returnValue(
      of({success: true}),
    );

    mockSearchService.searchFleet.and.returnValue(
      of({
        flat: {
          total: 3,
          rows: [
            {
              id: 'dev-1',
              cells: [
                {
                  link: {
                    text: 'dev-1',
                    target: {device: {id: 'dev-1', hostName: 'ats-host-1'}},
                  },
                },
              ],
            },
            {
              id: 'dev-2',
              cells: [
                {
                  link: {
                    text: 'dev-2',
                    target: {device: {id: 'dev-2', hostName: 'ats-host-1'}},
                  },
                },
              ],
            },
            {
              id: 'dev-readonly',
              cells: [
                {
                  link: {
                    text: 'dev-readonly',
                    target: {
                      device: {id: 'dev-readonly', hostName: 'ats-host-2'},
                    },
                  },
                },
              ],
            },
          ],
        },
      }),
    );

    mockConfigService.batchGetDeviceDimensionConfigs.and.callFake((req) => {
      if (req.dimensionName === 'recovery') {
        return of({
          entries: [
            {
              deviceId: 'dev-1',
              hostName: 'ats-host-1',
              values: ['false'],
              status:
                DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
            },
            {
              deviceId: 'dev-2',
              hostName: 'ats-host-1',
              values: ['true'],
              status:
                DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
            },
            {
              deviceId: 'dev-readonly',
              hostName: 'ats-host-2',
              values: [],
              status:
                DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
              readOnlyReason: 'Unrecognizable device ID',
            },
          ],
        });
      }
      return of({
        entries: [
          {
            deviceId: 'dev-1',
            hostName: 'ats-host-1',
            values: [],
            status:
              DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
          },
          {
            deviceId: 'dev-2',
            hostName: 'ats-host-1',
            values: ['cts_pool'],
            status:
              DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
          },
          {
            deviceId: 'dev-readonly',
            hostName: 'ats-host-2',
            values: [],
            status:
              DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
            readOnlyReason: 'Unrecognizable device ID',
          },
        ],
      });
    });

    mockConfigService.batchUpdateDeviceDimensionConfigs.and.returnValue(
      of({errors: {}}),
    );

    await TestBed.configureTestingModule({
      imports: [
        DeviceConfigTemplateDialog,
        NoopAnimationsModule,
        MatTestDialogOpenerModule,
      ],
      providers: [
        {provide: CONFIG_SERVICE, useValue: mockConfigService},
        {provide: SEARCH_SERVICE, useValue: mockSearchService},
      ],
    }).compileComponents();

    overlayElement = TestBed.inject(OverlayContainer).getContainerElement();
    overlayElement.replaceChildren();

    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceConfigTemplateDialog, {
        data: {universe: '', fleet: 'FLEET_SELF'},
      }),
    );
    component = fixture.componentInstance.dialogRef.componentInstance;
    mockDialogRef = fixture.componentInstance.dialogRef;
    spyOn(mockDialogRef, 'close');
    appRef = TestBed.inject(ApplicationRef);
    detectChanges();
  });

  afterEach(() => {
    overlayElement.replaceChildren();
  });

  it('loads curated dimensions, pre-populates template values, and renders non-retroactive warning banner', () => {
    expect(component.mode()).toBe('template');
    expect(component.dimensionFields().length).toBe(2);
    expect(component.dimensionFields()[0].values).toEqual(['true']);
    expect(overlayElement.textContent).toContain(
      'Applies automatically only to newly connected unconfigured devices',
    );
  });

  it('saves template only without modifying existing devices', () => {
    const poolField = component.dimensionFields()[1];
    component.onDraftInput(poolField.fieldKey, 'cts_pool');
    component.saveTemplateOnly();
    detectChanges();

    expect(mockConfigService.updateDeviceConfigTemplate).toHaveBeenCalledWith({
      deviceConfigTemplate: {
        dimensions: {
          supported: [
            {name: 'recovery', value: 'true'},
            {name: 'pool', value: 'cts_pool'},
          ],
          required: [],
        },
      },
      universe: '',
    });
    expect(mockSearchService.searchFleet).not.toHaveBeenCalled();
    expect(component.mode()).toBe('template_saved');

    component.close();
    expect(mockDialogRef.close).toHaveBeenCalledWith(undefined);
  });

  it('saves template, loads all controller devices into 3-state review with Will update selected by default, and applies updates', () => {
    const poolField = component.dimensionFields()[1];
    component.onDraftInput(poolField.fieldKey, 'cts_pool');
    component.saveAndReviewExistingDevices();
    detectChanges();

    expect(mockConfigService.updateDeviceConfigTemplate).toHaveBeenCalled();
    expect(mockSearchService.searchFleet).toHaveBeenCalled();
    expect(component.mode()).toBe('review');
    expect(component.reviewSummary().willUpdateCount).toBe(1);
    expect(component.reviewSummary().alreadyMatchCount).toBe(1);
    expect(component.reviewSummary().readOnlyCount).toBe(1);
    expect(component.checkedWillUpdateRows().map((r) => r.deviceId)).toEqual([
      'dev-1',
    ]);

    component.applyToSelectedDevices();
    detectChanges();

    expect(
      mockConfigService.batchUpdateDeviceDimensionConfigs,
    ).toHaveBeenCalledTimes(2);
    expect(component.mode()).toBe('success');

    component.close();
    expect(mockDialogRef.close).toHaveBeenCalledWith({updated: true});
  });

  it('allows unchecking Will update devices in review before applying', () => {
    component.saveAndReviewExistingDevices();
    detectChanges();

    const updatableRow = component.reviewSummary().willUpdateRows[0];
    expect(component.isRowSelected(updatableRow)).toBeTrue();

    component.toggleRowSelection(updatableRow);
    detectChanges();

    expect(component.checkedWillUpdateRows().length).toBe(0);
    expect(component.reviewSelectionState().all).toBeFalse();
  });
});
