import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {CollapsiblePanel} from './collapsible_panel';

describe('CollapsiblePanel Component', () => {
  let component: CollapsiblePanel;
  let fixture: ComponentFixture<CollapsiblePanel>;

  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            CollapsiblePanel,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          providers: [provideRouter([])],
        })
        .compileComponents();

    fixture = TestBed.createComponent(CollapsiblePanel);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  it('should expand the panel by default', () => {
    expect(component.expanded).toBeTrue();
  });

  it('should collapse the panel when the expand icon is clicked', () => {
    fixture.debugElement.nativeElement.querySelector('.expand-icon').click();
    expect(component.expanded).toBeFalse();
  });
});
