import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {InfoCard} from './info_card';

describe('InfoCard Component', () => {
  let component: InfoCard;
  let fixture: ComponentFixture<InfoCard>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        InfoCard,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(InfoCard);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  it('should expand the panel by default', () => {
    expect(component.expanded).toBeTrue();
  });

  it('should collapse the panel when collapsible and clicked', () => {
    component.collapsible = true;
    fixture.debugElement.nativeElement.querySelector('.panel-header').click();
    expect(component.expanded).toBeFalse();
  });

  it('should not collapse the panel when not collapsible and clicked', () => {
    component.collapsible = false;
    fixture.debugElement.nativeElement.querySelector('.panel-header').click();
    expect(component.expanded).toBeTrue();
  });
});
