import {ComponentFixture, TestBed} from '@angular/core/testing';
import {SaveFlagsConfirmContent} from './save_flags_confirm_content';

describe('SaveFlagsConfirmContent', () => {
  let component: SaveFlagsConfirmContent;
  let fixture: ComponentFixture<SaveFlagsConfirmContent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SaveFlagsConfirmContent],
    }).compileComponents();

    fixture = TestBed.createComponent(SaveFlagsConfirmContent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
