import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {HostDetail} from './host_detail';

describe('HostDetail Component', () => {
  let fixture: ComponentFixture<HostDetail>;
  let component: HostDetail;
  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            HostDetail,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          providers: [provideRouter([])],
        })
        .compileComponents();

    fixture = TestBed.createComponent(HostDetail);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
