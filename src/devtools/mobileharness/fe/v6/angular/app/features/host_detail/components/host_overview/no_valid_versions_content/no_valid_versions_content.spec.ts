import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';

import {NoValidVersionsContent} from './no_valid_versions_content';

describe('NoValidVersionsContent', () => {
  let component: NoValidVersionsContent;
  let fixture: ComponentFixture<NoValidVersionsContent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NoValidVersionsContent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(NoValidVersionsContent);
    component = fixture.componentInstance;
    component.hostName = 'test-host';
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display the no valid versions message with hostname', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain(
      'No valid release versions were found for test-host',
    );
    const strongEl = compiled.querySelector('strong');
    expect(strongEl?.textContent).toContain('test-host');
  });
});
