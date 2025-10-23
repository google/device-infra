import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {HostPermissionList} from './host_permissions';

describe('HostPermissions Component', () => {
  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            HostPermissionList,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          providers: [provideRouter([])],
        })
        .compileComponents();
  });

  it('should be created', () => {
    const fixture = TestBed.createComponent(HostPermissionList);
    const comp = fixture.componentInstance;
    fixture.detectChanges();
    expect(comp).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.title').innerText)
        .toBe(
            'Host Permissions',
        );
  });
});
