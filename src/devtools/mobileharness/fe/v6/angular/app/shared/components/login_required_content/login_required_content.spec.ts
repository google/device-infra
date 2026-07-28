import {ComponentFixture, TestBed} from '@angular/core/testing';
import {LoginRequiredContent} from './login_required_content';

describe('LoginRequiredContent', () => {
  let component: LoginRequiredContent;
  let fixture: ComponentFixture<LoginRequiredContent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginRequiredContent],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginRequiredContent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render explanation text', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('You are not logged in');
  });
});
