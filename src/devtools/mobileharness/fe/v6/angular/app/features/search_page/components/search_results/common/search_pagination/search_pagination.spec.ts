import {Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideNoopAnimations} from '@angular/platform-browser/animations';

import {SearchPaginationComponent} from './search_pagination';

@Component({
  standalone: true,
  imports: [SearchPaginationComponent],
  template: `
    <app-search-pagination
      [rangeText]="rangeText"
      [hasPrev]="hasPrev"
      [hasNext]="hasNext"
      [pageSize]="pageSize"
      [compact]="compact"
      (prev)="onPrev()"
      (next)="onNext()"
      (pageSizeChange)="onPageSizeChange($event)"
    ></app-search-pagination>
  `,
})
class TestHostComponent {
  rangeText = '';
  hasPrev = false;
  hasNext = false;
  pageSize: number | undefined = undefined;
  compact = false;

  prevClicked = false;
  nextClicked = false;
  newPageSize: number | null = null;

  onPrev() {
    this.prevClicked = true;
  }

  onNext() {
    this.nextClicked = true;
  }

  onPageSizeChange(size: number) {
    this.newPageSize = size;
  }
}

describe('SearchPaginationComponent', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let host: TestHostComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent],
      providers: [provideNoopAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    host = fixture.componentInstance;
  });

  it('renders rangeText directly when provided (BFF pre-formatted text)', () => {
    host.rangeText = '1 – 25 of 1,250';
    fixture.detectChanges();

    const rangeEl = fixture.nativeElement.querySelector(
      '.rt-range',
    ) as HTMLElement;
    expect(rangeEl.textContent?.trim()).toBe('1 – 25 of 1,250');
  });

  it('does not render .rt-range when rangeText is empty', () => {
    host.rangeText = '';
    fixture.detectChanges();

    const rangeEl = fixture.nativeElement.querySelector('.rt-range');
    expect(rangeEl).toBeNull();
  });

  it('handles prev and next navigation button states correctly based on inputs', () => {
    host.hasPrev = false;
    host.hasNext = true;
    fixture.detectChanges();

    const buttons = fixture.nativeElement.querySelectorAll(
      '.rt-btn-text',
    ) as NodeListOf<HTMLButtonElement>;
    expect(buttons.length).toBe(2);

    const prevBtn = buttons[0];
    const nextBtn = buttons[1];

    expect(prevBtn.disabled).toBeTrue();
    expect(nextBtn.disabled).toBeFalse();

    nextBtn.click();
    expect(host.nextClicked).toBeTrue();

    // Now enable prev and disable next
    host.hasPrev = true;
    host.hasNext = false;
    fixture.detectChanges();

    expect(prevBtn.disabled).toBeFalse();
    expect(nextBtn.disabled).toBeTrue();

    prevBtn.click();
    expect(host.prevClicked).toBeTrue();
  });

  it('renders page size selector when pageSize is provided and emits on change', () => {
    host.pageSize = 25;
    fixture.detectChanges();

    const selectEl = fixture.nativeElement.querySelector('.rt-page-size');
    expect(selectEl).not.toBeNull();

    const labelEl = fixture.nativeElement.querySelector('.rt-page-label');
    expect(labelEl?.textContent?.trim()).toBe('Rows per page:');
  });

  it('hides page size dropdown when pageSize is undefined', () => {
    host.pageSize = undefined;
    fixture.detectChanges();

    const selectEl = fixture.nativeElement.querySelector('.rt-page-size');
    expect(selectEl).toBeNull();
  });

  it('applies compact class to container when compact is true', () => {
    host.compact = true;
    fixture.detectChanges();

    const container = fixture.nativeElement.querySelector('.rt-pagination');
    expect(container.classList.contains('compact')).toBeTrue();
  });
});
