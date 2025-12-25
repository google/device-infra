import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {MasterDetailLayout} from './master_detail_layout';

describe('MasterDetailLayout Component', () => {
  let fixture: ComponentFixture<MasterDetailLayout>;
  let component: MasterDetailLayout;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        MasterDetailLayout,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(MasterDetailLayout);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
