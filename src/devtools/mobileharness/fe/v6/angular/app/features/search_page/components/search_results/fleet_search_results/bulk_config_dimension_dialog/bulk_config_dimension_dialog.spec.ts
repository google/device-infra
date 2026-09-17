import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialogRef} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of, throwError} from 'rxjs';

import {
  DimensionScope,
  GetBatchDimensionContextResponse,
} from '../../../../../../core/models/device_config_models';
import {
  CONFIG_SERVICE,
  ConfigService,
} from '../../../../../../core/services/config/config_service';
import {
  BulkConfigDimensionDialog,
  BulkConfigDimensionDialogData,
  canonicalValueSet,
  parseDimensionTokens,
} from './bulk_config_dimension_dialog';

describe('BulkConfigDimensionDialog', () => {
  let fixture: ComponentFixture<MatTestDialogOpener<BulkConfigDimensionDialog>>;
  let component: BulkConfigDimensionDialog;
  let mockConfigService: jasmine.SpyObj<ConfigService>;
  let mockDialogRef: MatDialogRef<BulkConfigDimensionDialog, unknown>;

  const mockDialogData: BulkConfigDimensionDialogData = {
    deviceIds: ['device-1', 'device-2', 'device-3'],
    dimension: {
      key: 'recovery',
      displayName: 'recovery',
      scope: DimensionScope.SUPPORTED,
      configuredDeviceCount: 8420,
    },
  };

  const mockBatchContext: GetBatchDimensionContextResponse = {
    current: [
      {
        deviceId: 'device-1',
        values: ['true'],
        writable: true,
      },
      {
        deviceId: 'device-2',
        values: ['false'],
        writable: false,
        unwritableReason: 'Managed by Config Pusher',
      },
      {
        deviceId: 'device-3',
        values: [],
        writable: true,
      },
    ],
    candidateValues: [
      {values: ['true'], deviceCount: 5000},
      {values: ['false'], deviceCount: 2000},
      {values: [], deviceCount: 800},
    ],
  };

  beforeEach(async () => {
    mockConfigService = jasmine.createSpyObj('ConfigService', [
      'getBatchDimensionContext',
      'batchUpdateDeviceConfig',
    ]);
    mockConfigService.getBatchDimensionContext.and.returnValue(
      of(mockBatchContext),
    );
    mockConfigService.batchUpdateDeviceConfig.and.returnValue(of({errors: {}}));

    await TestBed.configureTestingModule({
      imports: [
        BulkConfigDimensionDialog,
        NoopAnimationsModule,
        MatTestDialogOpenerModule,
      ],
      providers: [
        {provide: CONFIG_SERVICE, useValue: mockConfigService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(BulkConfigDimensionDialog, {
        data: mockDialogData,
      }),
    );
    component = fixture.componentInstance.dialogRef.componentInstance;
    mockDialogRef = fixture.componentInstance.dialogRef;
    spyOn(mockDialogRef, 'close');
    fixture.detectChanges();
  });

  describe('Token Parser & Canonical Set', () => {
    it('should tokenize comma- and semicolon-separated strings', () => {
      expect(parseDimensionTokens('foo, bar; baz')).toEqual([
        'foo',
        'bar',
        'baz',
      ]);
    });

    it('should preserve separators within quotes', () => {
      expect(
        parseDimensionTokens('foo, "bar, baz", \'qux; test\''),
      ).toEqual(['foo', 'bar, baz', 'qux; test']);
    });

    it('should produce identical canonical representation regardless of order or case', () => {
      expect(canonicalValueSet(['B', 'a'])).toBe(
        canonicalValueSet(['A', 'b']),
      );
    });
  });

  describe('Initialization and Context Loading', () => {
    it('should load batch dimension context on initialization', () => {
      expect(mockConfigService.getBatchDimensionContext).toHaveBeenCalledWith({
        deviceIds: ['device-1', 'device-2', 'device-3'],
        key: 'recovery',
      });
      expect(component.deviceItems().length).toBe(3);
      expect(component.candidateValues().length).toBe(3);
      expect(component.isLoading()).toBeFalse();
    });

    it('should start with empty target values when selection has mixed values', () => {
      expect(component.targetValues()).toEqual([]);
    });

    it('should default target values when all devices share identical non-empty values', () => {
      mockConfigService.getBatchDimensionContext.and.returnValue(
        of({
          current: [
            {deviceId: 'd1', values: ['pool-a'], writable: true},
            {deviceId: 'd2', values: ['pool-a'], writable: true},
          ],
          candidateValues: [],
        }),
      );

      component.loadBatchDimensionContext();
      expect(component.targetValues()).toEqual(['pool-a']);
    });
  });

  describe('Chip Input Interactions', () => {
    it('should commit chips on Enter key', () => {
      component.inputValue.set('custom_val');
      const event = new KeyboardEvent('keydown', {key: 'Enter'});
      spyOn(event, 'preventDefault');

      component.onInputKeydown(event);

      expect(event.preventDefault).toHaveBeenCalled();
      expect(component.targetValues()).toEqual(['custom_val']);
      expect(component.inputValue()).toBe('');
    });

    it('should commit chips on comma outside quotes', () => {
      const inputEl = document.createElement('input');
      inputEl.value = 'token1';
      const event = {
        key: ',',
        target: inputEl,
        preventDefault: jasmine.createSpy('preventDefault'),
      } as unknown as KeyboardEvent;

      component.inputValue.set('token1');
      component.onInputKeydown(event);

      expect(component.targetValues()).toEqual(['token1']);
    });

    it('should pop last chip on Backspace when input is empty', () => {
      component.targetValues.set(['val1', 'val2']);
      component.inputValue.set('');

      const inputEl = document.createElement('input');
      inputEl.value = '';
      inputEl.selectionStart = 0;
      inputEl.selectionEnd = 0;

      const event = {
        key: 'Backspace',
        target: inputEl,
        preventDefault: jasmine.createSpy('preventDefault'),
      } as unknown as KeyboardEvent;

      component.onInputKeydown(event);

      expect(component.targetValues()).toEqual(['val1']);
      expect(component.inputValue()).toBe('val2');
    });

    it('should tokenize pasted text', () => {
      const clipboardData = {
        getData: (type: string) => 'valA, valB\nvalC',
      };
      const event = {
        preventDefault: jasmine.createSpy('preventDefault'),
        clipboardData,
      } as unknown as ClipboardEvent;

      component.onInputPaste(event);

      expect(component.targetValues()).toEqual(['valA', 'valB', 'valC']);
    });

    it('should remove chip at index', () => {
      component.targetValues.set(['a', 'b', 'c']);
      component.removeChip(1);
      expect(component.targetValues()).toEqual(['a', 'c']);
    });

    it('should clear all target values', () => {
      component.targetValues.set(['a', 'b']);
      component.clearTarget();
      expect(component.targetValues()).toEqual([]);
    });

    it('should adopt value set suggestion via useValueSet', () => {
      component.useValueSet(['suggested1', 'suggested2']);
      expect(component.targetValues()).toEqual(['suggested1', 'suggested2']);
    });
  });

  describe('Wizard Navigation & Review', () => {
    it('should commit uncommitted input when navigating to step 2', () => {
      component.inputValue.set('uncommitted');
      component.goToStep2();

      expect(component.step()).toBe(2);
      expect(component.targetValues()).toEqual(['uncommitted']);
    });

    it('should correctly calculate changed, same, and skipped devices in Step 2', () => {
      component.targetValues.set(['true']);
      component.goToStep2();

      expect(component.skippedCount()).toBe(1); // device-2 (writable: false)
      expect(component.sameCount()).toBe(1); // device-1 (already 'true')
      expect(component.changedCount()).toBe(1); // device-3 (from [] to 'true')
      expect(component.hasWritableDevices()).toBeTrue();
    });

    it('should return to step 1 on backToStep1', () => {
      component.goToStep2();
      component.backToStep1();
      expect(component.step()).toBe(1);
    });
  });

  describe('Apply Mutation & Outcomes', () => {
    it('should apply batch update to only writable devices and transition to success', () => {
      component.targetValues.set(['true']);

      component.apply();

      expect(mockConfigService.batchUpdateDeviceConfig).toHaveBeenCalledWith({
        deviceIds: ['device-1', 'device-3'],
        dimension: {
          key: 'recovery',
          values: ['true'],
          scope: DimensionScope.SUPPORTED,
        },
      });

      expect(component.applyResult()).toBe('success');

      component.done();
      expect(mockDialogRef.close).toHaveBeenCalledWith({updated: true});
    });

    it('should show error screen when backend returns per-device failures', () => {
      mockConfigService.batchUpdateDeviceConfig.and.returnValue(
        of({
          errors: {
            'device-3': 'Permission denied by policy',
          },
        }),
      );

      component.targetValues.set(['true']);
      component.apply();

      expect(component.applyResult()).toBe('errors');
      expect(component.applyErrors().length).toBe(1);
      expect(component.applyErrors()[0].deviceId).toBe('device-3');
      expect(component.applyErrors()[0].reason).toBe(
        'Permission denied by policy',
      );

      component.close();
      expect(mockDialogRef.close).toHaveBeenCalled();
    });

    it('should handle backend load failure gracefully', () => {
      mockConfigService.getBatchDimensionContext.and.returnValue(
        throwError(() => new Error('Service down')),
      );

      component.loadBatchDimensionContext();
      expect(component.loadError()).toBe('Service down');
      expect(component.isLoading()).toBeFalse();
    });
  });

  describe('Footer Actions Styling', () => {
    it('should render action buttons with common dialog-button classes', () => {
      fixture.detectChanges();
      const buttons = document.querySelectorAll('.dialog-button');
      expect(buttons.length).toBe(2);

      const cancelBtn = document.querySelector('.dialog-button-secondary');
      const nextBtn = document.querySelector('.dialog-button-primary');
      expect(cancelBtn).toBeTruthy();
      expect(nextBtn).toBeTruthy();
    });

    it('should maintain consistent title as "Configure dimension" across steps', () => {
      expect(component.headline()).toBe('Configure dimension');
      component.goToStep2();
      expect(component.headline()).toBe('Configure dimension');
    });
  });
});
