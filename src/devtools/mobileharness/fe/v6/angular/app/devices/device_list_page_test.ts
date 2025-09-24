import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';

import {DeviceListPage} from './device_list_page';

describe('DeviceListPage Component', () => {
  let component: DeviceListPage;
  let fixture: ComponentFixture<DeviceListPage>;

  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            DeviceListPage,
            NoopAnimationsModule,
          ],
        })
        .compileComponents();

    fixture = TestBed.createComponent(DeviceListPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
