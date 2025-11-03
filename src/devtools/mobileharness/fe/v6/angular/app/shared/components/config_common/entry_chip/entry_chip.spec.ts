import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {EntryChip} from './entry_chip';

describe('UserAdd Component', () => {
  let component: EntryChip;
  let fixture: ComponentFixture<EntryChip>;

  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            EntryChip,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          providers: [provideRouter([])],
        })
        .compileComponents();

    fixture = TestBed.createComponent(EntryChip);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should add a new entry correctly', () => {
    component.entry = 'test_user';
    component.add();
    fixture.detectChanges();
    expect(component.entries.length).toBe(1);
    expect(component.entries[0]).toBe('test_user');
  });

  it('should remove a entry correctly', () => {
    component.entries = ['test_user_1', 'test_user_2'];
    fixture.detectChanges();
    component.remove(0);
    fixture.detectChanges();
    expect(component.entries.length).toBe(1);
    expect(component.entries[0]).toBe('test_user_2');
  });
});
