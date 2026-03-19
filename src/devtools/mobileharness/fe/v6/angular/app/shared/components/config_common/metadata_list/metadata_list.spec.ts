import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {MetadataList} from 'app/shared/components/config_common/metadata_list/metadata_list';

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
    fixture.detectChanges();
  });

  it('should add a new metadata correctly', () => {
    component.add();
    fixture.detectChanges();
    expect(component.metadataList.length).toBe(1);
    expect(component.metadataList[0]['value']).toBe('');
  });

  it('should remove a metadata correctly', () => {
    component.metadataList = [
      {name: 'test_metadata_1', value: 'test_value_1'},
      {name: 'test_metadata_2', value: 'test_value_2'},
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
      {name: 'test_metadata_1', value: 'test_value_1'},
      {name: 'test_metadata_2', value: 'test_value_2'},
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
    component.metadataList = [{type: 'value1'}];
    component.validate();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const table = fixture.nativeElement.querySelector('table');
    expect(table).toBeTruthy();
    expect(component.metadataList[0]['type']).toBe('value1');
  });
});
