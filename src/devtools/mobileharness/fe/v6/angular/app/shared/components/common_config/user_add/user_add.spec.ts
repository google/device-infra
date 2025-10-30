import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {UserAdd} from './user_add';

describe('UserAdd Component', () => {
  let component: UserAdd;
  let fixture: ComponentFixture<UserAdd>;

  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            UserAdd,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          providers: [provideRouter([])],
        })
        .compileComponents();

    fixture = TestBed.createComponent(UserAdd);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should add a new user correctly', () => {
    component.user = 'test_user';
    component.add();
    expect(component.users.length).toBe(1);
    expect(component.users[0]).toBe('test_user');
  });

  it('should remove a user correctly', () => {
    component.users = ['test_user_1', 'test_user_2'];
    component.remove(0);
    expect(component.users.length).toBe(1);
    expect(component.users[0]).toBe('test_user_2');
  });
});
