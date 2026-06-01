import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {SCENARIO_IN_SERVICE_IDLE} from '../../../../../../core/services/mock_data/devices/01_in_service_idle';

import {Dimensions} from './dimensions';

describe('Dimensions Component', () => {
  let fixture: ComponentFixture<Dimensions>;
  let component: Dimensions;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        Dimensions,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Dimensions);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('dimensions', SCENARIO_IN_SERVICE_IDLE.config!.dimensions!);
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  it('should compute dimensions correctly, defaulting to empty arrays', () => {
    fixture.componentRef.setInput('dimensions', {});
    fixture.detectChanges();
    expect(component.dimensions()).toEqual({supported: [], required: []});
  });

  it('should emit dimensionsChange when onMetadataListChange is called', () => {
    fixture.componentRef.setInput('dimensions', {
      supported: [{name: 's1', value: 'v1'}],
      required: [{name: 'r1', value: 'v2'}],
    });
    fixture.detectChanges();

    spyOn(component.dimensionsChange, 'emit');

    const updatedSupported = [
      {name: 's1', value: 'v1'},
      {name: 's2', value: 'v2'},
    ];
    component.onMetadataListChange('supported', updatedSupported);

    expect(component.dimensionsChange.emit).toHaveBeenCalledWith({
      supported: updatedSupported,
      required: [{name: 'r1', value: 'v2'}],
    });

    const updatedRequired = [{name: 'r2', value: 'v3'}];
    component.onMetadataListChange('required', updatedRequired);

    expect(component.dimensionsChange.emit).toHaveBeenCalledWith({
      supported: [{name: 's1', value: 'v1'}],
      required: updatedRequired,
    });
  });

  it('should emit hasError when onHasErrorChanged is called', () => {
    spyOn(component.hasError, 'emit');

    component.onHasErrorChanged('supported', true);
    expect(component.supportError).toBeTrue();
    expect(component.hasError.emit).toHaveBeenCalledWith(true);

    component.onHasErrorChanged('required', false);
    expect(component.requiredError).toBeFalse();
    expect(component.hasError.emit).toHaveBeenCalledWith(true);

    component.onHasErrorChanged('supported', false);
    expect(component.supportError).toBeFalse();
    expect(component.hasError.emit).toHaveBeenCalledWith(false);
  });
});

