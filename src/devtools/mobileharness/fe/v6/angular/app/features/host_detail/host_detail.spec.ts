import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {FakeHostService} from '../../core/services/host/fake_host_service';
import {HOST_SERVICE} from '../../core/services/host/host_service';

import {HostDetail} from './host_detail';

describe('HostDetail Component', () => {
  let fixture: ComponentFixture<HostDetail>;
  let component: HostDetail;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        HostDetail,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [
        provideRouter([]),
        {provide: HOST_SERVICE, useClass: FakeHostService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(HostDetail);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
