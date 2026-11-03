import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {ReviewTable} from './review_table';

describe('ReviewTable Component', () => {
  let component: ReviewTable;
  let fixture: ComponentFixture<ReviewTable>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ReviewTable,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(ReviewTable);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  it('renders Feature and Applied Configuration headers in Title Case without uppercase text-transform', () => {
    const headerRow = fixture.nativeElement.querySelector(
      '.review-table .header-row',
    ) as HTMLElement;
    expect(headerRow).toBeTruthy();
    expect(getComputedStyle(headerRow).textTransform).toBe('none');

    const headerCells = Array.from<HTMLElement>(
      fixture.nativeElement.querySelectorAll(
        '.review-table th[mat-header-cell]',
      ),
    );
    expect(headerCells.map((el) => el.textContent?.trim())).toEqual([
      'Feature',
      'Applied Configuration',
    ]);
    for (const cell of headerCells) {
      expect(getComputedStyle(cell).textTransform).toBe('none');
    }
  });
});
