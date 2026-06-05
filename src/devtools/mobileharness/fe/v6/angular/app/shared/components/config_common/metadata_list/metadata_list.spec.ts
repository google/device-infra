import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {MetadataList} from '@deviceinfra/app/shared/components/config_common/metadata_list/metadata_list';

describe('MetadataList Component', () => {
  let component: MetadataList<Record<string, string>>;
  let fixture: ComponentFixture<MetadataList<Record<string, string>>>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        MetadataList,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(MetadataList<Record<string, string>>);
    component = fixture.componentInstance;
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.nativeElement.remove();
  });

  it('should add a new metadata correctly', () => {
    component.add();
    fixture.detectChanges();
    expect(component.metadataList.length).toBe(1);
    expect(component.metadataList[0]['value']).toBe('');
  });

  it('should remove a metadata correctly', () => {
    component.metadataList = [
      {'name': 'test_metadata_1', 'value': 'test_value_1'},
      {'name': 'test_metadata_2', 'value': 'test_value_2'},
    ];
    fixture.detectChanges();
    component.remove(0);
    fixture.detectChanges();
    expect(component.metadataList.length).toBe(1);
    expect(component.metadataList[0]['name']).toBe('test_metadata_2');
    expect(component.metadataList[0]['value']).toBe('test_value_2');
  });

  it('should update a metadata correctly', () => {
    component.metadataList = [
      {'name': 'test_metadata_1', 'value': 'test_value_1'},
      {'name': 'test_metadata_2', 'value': 'test_value_2'},
    ];
    fixture.detectChanges();
    component.metadataList[0]['name'] = 'test_metadata_3';
    component.metadataList[0]['value'] = 'test_value_3';
    component.onMetadataChanged();
    fixture.detectChanges();
    expect(component.metadataList.length).toBe(2);
    expect(component.metadataList[0]['name']).toBe('test_metadata_3');
    expect(component.metadataList[0]['value']).toBe('test_value_3');
  });

  it('should check duplicate correctly', () => {
    component.metadataList = [
      {name: 'test_metadata', value: 'test_value'},
      {name: 'test_metadata', value: 'test_value'},
    ];
    fixture.detectChanges();
    expect(component.duplicate(0)).toBe(true);
    expect(component.duplicate(1)).toBe(true);
  });

  it('should check pattern correctly', () => {
    component.columns = [
      {
        columnDef: 'name',
        header: 'NAME',
        cell: 'name',
        type: 'input',
        pattern: '^[a-z]+$',
        patternError: 'Only lowercase letters allowed.',
      },
    ];
    component.metadataList = [
      {name: 'valid', value: ''},
      {name: 'INVALID123', value: ''},
    ];
    fixture.detectChanges();
    expect(component.checkPattern(0)).toBe('');
    expect(component.checkPattern(1)).toContain(
      'Only lowercase letters allowed.',
    );
  });

  it('should handle select options correctly', async () => {
    component.columns = [
      {
        columnDef: 'type',
        header: 'TYPE',
        cell: 'type',
        type: 'select',
        options: [
          {label: 'Label 1', value: 'value1'},
          {label: 'Label 2', value: 'value2'},
        ],
      },
    ];
    component.metadataList = [{'type': 'value1'}];
    component.validate();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const table = fixture.nativeElement.querySelector('table');
    expect(table).toBeTruthy();
    expect(component.metadataList[0]['type']).toBe('value1');
  });

  it('should focus the first input of the newly added row', async () => {
    const addButton = fixture.nativeElement.querySelector(
      '.add-metadata-button',
    ) as HTMLButtonElement;
    expect(addButton).toBeTruthy();
    addButton.click();

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const activeElement = document.activeElement;
    expect(activeElement).toBeTruthy();
    expect(activeElement?.tagName.toLowerCase()).toBe('input');

    const inputs = fixture.nativeElement.querySelectorAll('input');
    expect(inputs.length).toBe(2);
    expect(activeElement).toBe(inputs[0]);
  });

  it('should focus the first input of the newly added row when rows already exist', async () => {
    component.metadataList = [
      {'name': 'existing_name', 'value': 'existing_value'},
    ];
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const addButton = fixture.nativeElement.querySelector(
      '.add-metadata-button',
    ) as HTMLButtonElement;
    expect(addButton).toBeTruthy();
    addButton.click();

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const activeElement = document.activeElement;
    expect(activeElement).toBeTruthy();
    expect(activeElement?.tagName.toLowerCase()).toBe('input');

    const inputs = fixture.nativeElement.querySelectorAll('input');
    expect(inputs.length).toBe(4);
    expect(activeElement).toBe(inputs[2]);
  });

  describe('editability', () => {
    beforeEach(async () => {
      const addButton = fixture.nativeElement.querySelector(
        '.add-metadata-button',
      ) as HTMLButtonElement;
      expect(addButton).toBeTruthy();

      addButton.click();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      addButton.click();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    });

    it('debug: should have metadataList', () => {
      expect(component.metadataList.length).toBe(2);
    });

    it('debug: should have columns', () => {
      expect(component.columns.length).toBe(2);
      expect(component.displayedColumns.length).toBe(3); // 2 + 'action'
    });

    it('debug: should have table rows', () => {
      const rows = fixture.nativeElement.querySelectorAll('.metadata-list-row');
      expect(rows.length).toBe(2);
    });

    it('should enable all items when section is editable and no overrides', async () => {
      component.uiStatus = {
        sectionStatus: {visible: true, editability: {editable: true}},
      };
      fixture.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const inputs = fixture.nativeElement.querySelectorAll('input');
      expect(inputs.length).toBe(4); // 2 items * 2 columns
      inputs.forEach((input: HTMLInputElement) => {
        expect(input.disabled).toBeFalse();
      });
    });

    it('should disable all items when section is not editable', async () => {
      fixture.componentRef.setInput('uiStatus', {
        sectionStatus: {visible: true, editability: {editable: false}},
      });
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const inputs = fixture.nativeElement.querySelectorAll('input');
      expect(inputs.length).toBe(4);
      inputs.forEach((input: HTMLInputElement) => {
        expect(input.disabled).toBeTrue();
      });

      const buttons = fixture.nativeElement.querySelectorAll('button');
      expect(buttons.length).toBe(2); // Only 2 remove buttons, add button should be hidden
      buttons.forEach((button: HTMLButtonElement) => {
        expect(button.disabled).toBeTrue();
      });
    });

    it('should disable specific item when it has editable false override', async () => {
      fixture.componentRef.setInput('uiStatus', {
        sectionStatus: {visible: true, editability: {editable: true}},
        itemEditabilityOverrides: {
          0: {editable: false},
        },
      });
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const inputs = fixture.nativeElement.querySelectorAll('input');
      expect(inputs.length).toBe(4);
      // Item 0 (inputs 0 and 1) should be disabled
      expect(inputs[0].disabled).toBeTrue();
      expect(inputs[1].disabled).toBeTrue();
      // Item 1 (inputs 2 and 3) should be enabled
      expect(inputs[2].disabled).toBeFalse();
      expect(inputs[3].disabled).toBeFalse();
    });

    it('should disable specific item when it has empty override (simulating proto3 omission of false)', async () => {
      fixture.componentRef.setInput('uiStatus', {
        sectionStatus: {visible: true, editability: {editable: true}},
        itemEditabilityOverrides: {
          0: {}, // empty override
        },
      });
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const inputs = fixture.nativeElement.querySelectorAll('input');
      expect(inputs.length).toBe(4);
      // Item 0 (inputs 0 and 1) should be disabled
      expect(inputs[0].disabled).toBeTrue();
      expect(inputs[1].disabled).toBeTrue();
      // Item 1 (inputs 2 and 3) should be enabled
      expect(inputs[2].disabled).toBeFalse();
      expect(inputs[3].disabled).toBeFalse();
    });

    it('should enable specific item when it has editable true override', async () => {
      fixture.componentRef.setInput('uiStatus', {
        sectionStatus: {visible: true, editability: {editable: true}},
        itemEditabilityOverrides: {
          0: {editable: true},
        },
      });
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const inputs = fixture.nativeElement.querySelectorAll('input');
      expect(inputs.length).toBe(4);
      inputs.forEach((input: HTMLInputElement) => {
        expect(input.disabled).toBeFalse();
      });
    });

    it('should disable item even with editable true override if section is not editable', async () => {
      fixture.componentRef.setInput('uiStatus', {
        sectionStatus: {visible: true, editability: {editable: false}},
        itemEditabilityOverrides: {
          0: {editable: true},
        },
      });
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const inputs = fixture.nativeElement.querySelectorAll('input');
      expect(inputs.length).toBe(4);
      inputs.forEach((input: HTMLInputElement) => {
        expect(input.disabled).toBeTrue();
      });
    });
  });
});
