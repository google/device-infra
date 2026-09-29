import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {provideRouter, Router} from '@angular/router';

import {CommonParamsService} from '../../../../core/services/common_params_service';
import {HeaderJumpBar} from './header_jump_bar';

describe('HeaderJumpBar', () => {
  let component: HeaderJumpBar;
  let fixture: ComponentFixture<HeaderJumpBar>;
  let router: Router;
  let commonParamsService: CommonParamsService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HeaderJumpBar],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(HeaderJumpBar);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    commonParamsService = TestBed.inject(CommonParamsService);
    spyOn(commonParamsService, 'getCommonParams').and.returnValue({
      'fake_data': 'true',
    });
    fixture.detectChanges();
  });

  it('renders Any ID scope chip and default placeholder by default', () => {
    expect(component.scope()).toBe('any');
    const chipLabel = fixture.debugElement.query(By.css('.jump-scope-label'));
    expect(chipLabel.nativeElement.textContent.trim()).toBe('Any ID');

    const inputEl = fixture.debugElement.query(
      By.css('.header-search-input'),
    ).nativeElement as HTMLInputElement;
    expect(inputEl.placeholder).toBe(
      'Jump to a device, host, test, job, or session by ID',
    );
  });

  it('toggles scope dropdown menu and updates placeholder when selecting a scope', () => {
    const chipBtn = fixture.debugElement.query(By.css('.jump-scope-chip'));
    chipBtn.nativeElement.click();
    fixture.detectChanges();
    expect(component.isMenuOpen()).toBeTrue();

    const deviceOption = fixture.debugElement.query(
      By.css('.jump-scope-item[data-scope="device"]'),
    );
    expect(deviceOption).toBeTruthy();
    deviceOption.nativeElement.click();
    fixture.detectChanges();

    expect(component.isMenuOpen()).toBeFalse();
    expect(component.scope()).toBe('device');
    const chipLabel = fixture.debugElement.query(By.css('.jump-scope-label'));
    expect(chipLabel.nativeElement.textContent.trim()).toBe('Device ID');
  });

  it('shows Open and Clear buttons when input has text and clears on Clear click', () => {
    component.onInput('device-id-for-1-hit-1');
    fixture.detectChanges();

    expect(component.hasValue()).toBeTrue();
    const openBtn = fixture.debugElement.query(
      By.css('.jump-enter-hint.visible'),
    );
    expect(openBtn).toBeTruthy();
    const clearBtn = fixture.debugElement.query(By.css('.header-search-clear'));
    expect(clearBtn).toBeTruthy();

    clearBtn.nativeElement.click();
    fixture.detectChanges();
    expect(component.queryText()).toBe('');
    expect(component.hasValue()).toBeFalse();
  });

  it('navigates to /search with q and omits scope when scope is any', () => {
    const navSpy = spyOn(router, 'navigate');
    component.onInput('  device-id-for-1-hit-1  ');
    component.submitSearch();

    expect(navSpy).toHaveBeenCalledWith(['/search'], {
      queryParams: {
        'fake_data': 'true',
        'q': 'device-id-for-1-hit-1',
      },
    });
  });

  it('navigates to /search with q and scope when scope is narrowed', () => {
    const navSpy = spyOn(router, 'navigate');
    component.scope.set('host');
    component.onInput('host-name-for-ambiguous-1');
    component.submitSearch();

    expect(navSpy).toHaveBeenCalledWith(['/search'], {
      queryParams: {
        'fake_data': 'true',
        'q': 'host-name-for-ambiguous-1',
        'scope': 'host',
      },
    });
  });

  it('closes scope menu when Escape key is pressed even if focus is not on the input', () => {
    const chipBtn = fixture.debugElement.query(By.css('.jump-scope-chip'));
    chipBtn.nativeElement.focus();
    chipBtn.nativeElement.click();
    fixture.detectChanges();
    expect(component.isMenuOpen()).toBeTrue();

    document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape'}));
    fixture.detectChanges();

    expect(component.isMenuOpen()).toBeFalse();
  });

  it('triggers search immediately when scope is changed and queryText is non-empty', () => {
    const navSpy = spyOn(router, 'navigate');
    component.onInput('  tjs-id-for-ambiguous-1  ');
    fixture.detectChanges();

    const chipBtn = fixture.debugElement.query(By.css('.jump-scope-chip'));
    chipBtn.nativeElement.click();
    fixture.detectChanges();

    const jobOption = fixture.debugElement.query(
      By.css('.jump-scope-item[data-scope="job"]'),
    );
    jobOption.nativeElement.click();
    fixture.detectChanges();

    expect(component.scope()).toBe('job');
    expect(component.isMenuOpen()).toBeFalse();
    expect(navSpy).toHaveBeenCalledWith(['/search'], {
      queryParams: {
        'fake_data': 'true',
        'q': 'tjs-id-for-ambiguous-1',
        'scope': 'job',
      },
    });
  });
});
