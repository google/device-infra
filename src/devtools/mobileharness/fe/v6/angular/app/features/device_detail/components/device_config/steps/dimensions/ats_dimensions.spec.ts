import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of} from 'rxjs';

import {
  ConfigurableDimension,
  DeviceDimension,
  DimensionList,
} from '../../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../../core/services/config/config_service';

import {AtsDimensions} from './ats_dimensions';

describe('AtsDimensions Component', () => {
  let fixture: ComponentFixture<AtsDimensions>;
  let component: AtsDimensions;
  let getConfigurableDimensionsSpy: jasmine.Spy;
  let getDimensionValueSuggestionsSpy: jasmine.Spy;

  const curatedDims: ConfigurableDimension[] = [
    {
      dimensionName: 'recovery',
      displayName: 'recovery',
      dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
    },
    {
      dimensionName: 'skip_suw_app',
      displayName: 'skip_suw_app',
      dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
    },
    {
      dimensionName: 'pool',
      displayName: 'Required Dimension: pool',
      dimensionList: DimensionList.DIMENSION_LIST_REQUIRED,
    },
    {
      dimensionName: 'persist_test_harness',
      displayName: 'Required Dimension: persist_test_harness',
      dimensionList: DimensionList.DIMENSION_LIST_REQUIRED,
    },
  ];

  beforeEach(async () => {
    getConfigurableDimensionsSpy = jasmine
      .createSpy('getConfigurableDimensions')
      .and.returnValue(
        of({
          enabled: true,
          dimensions: curatedDims,
          allowCustomDimensions: false,
        }),
      );
    getDimensionValueSuggestionsSpy = jasmine
      .createSpy('getDimensionValueSuggestions')
      .and.callFake(({dimensionName}: {dimensionName: string}) => {
        if (dimensionName === 'pool') {
          return of({
            candidateValues: [
              {values: ['cts-presubmit'], deviceCount: 12},
              {values: ['rpc-error'], deviceCount: 0},
            ],
          });
        }
        return of({candidateValues: []});
      });

    await TestBed.configureTestingModule({
      imports: [AtsDimensions, NoopAnimationsModule],
      providers: [
        {
          provide: CONFIG_SERVICE,
          useValue: {
            getConfigurableDimensions: getConfigurableDimensionsSpy,
            getDimensionValueSuggestions: getDimensionValueSuggestionsSpy,
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AtsDimensions);
    component = fixture.componentInstance;
    component.dimensions = {
      supported: [
        {name: 'recovery', value: 'true'},
        {name: 'custom_label', value: 'keep_me'},
      ],
      required: [{name: 'pool', value: 'partner_shared'}],
    };
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('orders required curated dimensions first and avoids uppercase styling', () => {
    const names = Array.from<HTMLElement, string | undefined>(
      fixture.nativeElement.querySelectorAll('.ats-dim-name'),
      (el) => el.textContent?.trim(),
    );
    expect(names).toEqual([
      'pool',
      'persist_test_harness',
      'recovery',
      'skip_suw_app',
    ]);

    const badges = Array.from<HTMLElement>(
      fixture.nativeElement.querySelectorAll('.ats-dim-badge'),
    );
    expect(badges.map((el) => el.textContent?.trim())).toEqual([
      'Required',
      'Required',
      'Supported',
      'Supported',
    ]);
    for (const badge of badges) {
      expect(getComputedStyle(badge).textTransform).toBe('none');
    }
  });

  it('surfaces fleet suggestions with positive device count and excludes zero-count entries', () => {
    const poolField = component
      .dimensionFields()
      .find((f) => f.dim.dimensionName === 'pool');
    expect(poolField?.values).toEqual(['partner_shared']);
    expect(poolField?.suggestions).toEqual(['cts-presubmit']);
  });

  it('commits draft inputs and preserves non-curated dimensions on emit', () => {
    let emitted:
      | {supported: DeviceDimension[]; required: DeviceDimension[]}
      | undefined;
    component.dimensionsChange.subscribe((val) => {
      emitted = val;
    });

    const skipField = component
      .dimensionFields()
      .find((f) => f.dim.dimensionName === 'skip_suw_app')!;
    component.onDraftInput(skipField.fieldKey, 'disable');
    expect(component.hasPendingDraft()).toBeTrue();

    component.commitAllDrafts();
    expect(component.hasPendingDraft()).toBeFalse();
    expect(emitted).toEqual({
      supported: [
        {name: 'custom_label', value: 'keep_me'},
        {name: 'recovery', value: 'true'},
        {name: 'skip_suw_app', value: 'disable'},
      ],
      required: [{name: 'pool', value: 'partner_shared'}],
    });
  });

  it('clears individual and all curated dimensions while keeping non-curated dimensions', () => {
    let emitted:
      | {supported: DeviceDimension[]; required: DeviceDimension[]}
      | undefined;
    component.dimensionsChange.subscribe((val) => {
      emitted = val;
    });

    component.clearAllDimensions();
    expect(emitted).toEqual({
      supported: [{name: 'custom_label', value: 'keep_me'}],
      required: [],
    });
  });
});
