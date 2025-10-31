import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {ReviewTable} from './review_table';

describe('ReviewTable Component', () => {
  let component: ReviewTable;
  let fixture: ComponentFixture<ReviewTable>;

  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            ReviewTable,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          providers: [provideRouter([])],
        })
        .compileComponents();

    fixture = TestBed.createComponent(ReviewTable);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
