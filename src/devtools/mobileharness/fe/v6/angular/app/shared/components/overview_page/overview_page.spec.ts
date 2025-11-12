import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {OverviewPage} from './overview_page';

describe('OverviewPage Component', () => {
  let fixture: ComponentFixture<OverviewPage>;
  let component: OverviewPage;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        OverviewPage,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(OverviewPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
