import {ComponentFixture, TestBed, fakeAsync, tick} from '@angular/core/testing';
import {MatDialogRef} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of, throwError} from 'rxjs';

import {
  DimensionScope,
  GetConfigurableDimensionsResponse,
} from '../../../../../../core/models/device_config_models';
import {
  CONFIG_SERVICE,
  ConfigService,
} from '../../../../../../core/services/config/config_service';
import {MoreDimensionsDialog} from './more_dimensions_dialog';

describe('MoreDimensionsDialog', () => {
  let fixture: ComponentFixture<MatTestDialogOpener<MoreDimensionsDialog>>;
  let component: MoreDimensionsDialog;
  let mockConfigService: jasmine.SpyObj<ConfigService>;
  let mockDialogRef: MatDialogRef<MoreDimensionsDialog>;

  const mockDimensionsResponse: GetConfigurableDimensionsResponse = {
    dimensions: [
      {
        key: 'recovery',
        displayName: 'recovery',
        scope: DimensionScope.SUPPORTED,
        configuredDeviceCount: 8420,
      },
      {
        key: 'pool',
        displayName: 'pool',
        scope: DimensionScope.SUPPORTED,
        configuredDeviceCount: 5280,
      },
    ],
    allowCustomDimensions: true,
  };

  beforeEach(async () => {
    mockConfigService = jasmine.createSpyObj('ConfigService', ['getConfigurableDimensions']);
    mockConfigService.getConfigurableDimensions.and.returnValue(of(mockDimensionsResponse));

    await TestBed.configureTestingModule({
      imports: [
        MoreDimensionsDialog,
        NoopAnimationsModule,
        MatTestDialogOpenerModule,
      ],
      providers: [
        {provide: CONFIG_SERVICE, useValue: mockConfigService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(MoreDimensionsDialog, {
        data: {allowCustomDimensions: true},
      }),
    );
    component = fixture.componentInstance.dialogRef.componentInstance;
    mockDialogRef = fixture.componentInstance.dialogRef;
    spyOn(mockDialogRef, 'close');
    fixture.detectChanges();
  });

  it('should load configurable dimensions on initialization', () => {
    expect(mockConfigService.getConfigurableDimensions).toHaveBeenCalledWith({query: ''});
    expect(component.dimensions().length).toBe(2);
    expect(component.dimensions()[0].key).toBe('recovery');
    expect(component.isLoading()).toBeFalse();
  });

  it('should debounce search input and query backend', fakeAsync(() => {
    component.onSearchInput('rec');
    tick(200);

    expect(mockConfigService.getConfigurableDimensions).toHaveBeenCalledWith({query: 'rec'});
  }));

  it('should detect reserved system keys and prevent configuration', () => {
    component.onSearchInput('battery_level');

    expect(component.isReservedSystemKey()).toBeTrue();
    expect(component.showCustomAffordance()).toBeFalse();
  });

  it('should show custom dimension affordance when query is unique and permitted', () => {
    component.onSearchInput('my_custom_dim');

    expect(component.isReservedSystemKey()).toBeFalse();
    expect(component.isExactMatch()).toBeFalse();
    expect(component.showCustomAffordance()).toBeTrue();
  });

  it('should not show custom dimension affordance if exact match exists', () => {
    component.onSearchInput('recovery');

    expect(component.isExactMatch()).toBeTrue();
    expect(component.showCustomAffordance()).toBeFalse();
  });

  it('should close dialog with selected dimension', () => {
    const targetDim = mockDimensionsResponse.dimensions[0];
    component.selectDimension(targetDim);

    expect(mockDialogRef.close).toHaveBeenCalledWith(targetDim);
  });

  it('should close dialog with custom dimension when affordance is selected', () => {
    component.onSearchInput('my_custom_dim');
    component.selectCustomDimension();

    expect(mockDialogRef.close).toHaveBeenCalledWith(
      jasmine.objectContaining({
        key: 'my_custom_dim',
        displayName: 'my_custom_dim',
        scope: DimensionScope.SUPPORTED,
      }),
    );
  });

  it('should handle backend loading failure gracefully', () => {
    mockConfigService.getConfigurableDimensions.and.returnValue(
      throwError(() => new Error('Server unavailable')),
    );

    component.fetchDimensions('');

    expect(component.loadError()).toBe('Server unavailable');
    expect(component.isLoading()).toBeFalse();
  });

  it('should close dialog with null when close() is invoked', () => {
    component.close();
    expect(mockDialogRef.close).toHaveBeenCalledWith(null);
  });
});
