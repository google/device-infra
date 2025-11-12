import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {HostOverview} from './host_overview';

describe('HostOverview Component', () => {
  let fixture: ComponentFixture<HostOverview>;
  let component: HostOverview;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        HostOverview,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(HostOverview);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
