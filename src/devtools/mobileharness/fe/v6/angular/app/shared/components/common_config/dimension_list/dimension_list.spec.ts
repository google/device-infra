import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {DimensionList} from './dimension_list';

describe('DimensionList Component', () => {
  let component: DimensionList;
  let fixture: ComponentFixture<DimensionList>;

  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            DimensionList,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          providers: [provideRouter([])],
        })
        .compileComponents();

    fixture = TestBed.createComponent(DimensionList);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should add a new dimension correctly', () => {
    component.dimensions = [];
    component.add();
    expect(component.dimensions.length).toBe(1);
    expect(component.dimensions[0].value).toBe('');
    expect(component.currentEditingIndex).toBe(0);
  });

  it('should remove a dimension correctly', () => {
    component.dimensions = [
      {name: 'test_dimension_1', value: 'test_value_1'},
      {name: 'test_dimension_2', value: 'test_value_2'},
    ];
    component.remove(0);
    expect(component.dimensions.length).toBe(1);
    expect(component.dimensions[0].name).toBe('test_dimension_2');
    expect(component.dimensions[0].value).toBe('test_value_2');
    expect(component.currentEditingIndex).toBe(null);
  });

  it('should update a dimension correctly', () => {
    component.dimensions = [
      {name: 'test_dimension_1', value: 'test_value_1'},
      {name: 'test_dimension_2', value: 'test_value_2'},
    ];
    component.onDimensionChanged(0, 'test_dimension_3', 'test_value_3');
    expect(component.dimensions.length).toBe(2);
    expect(component.dimensions[0].name).toBe('test_dimension_3');
    expect(component.dimensions[0].value).toBe('test_value_3');
    expect(component.currentEditingIndex).toBe(0);
  });

  it('should check duplicate correctly', () => {
    component.dimensions = [
      {name: 'test_dimension', value: 'test_value'},
      {name: 'test_dimension', value: 'test_value'},
    ];
    expect(component.duplicate(0)).toBe(true);
    expect(component.duplicate(1)).toBe(true);
  });
});
