import {ChangeDetectionStrategy, Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';

import {Observable, of} from 'rxjs';

import {FileExplorer} from '../../../../core/models/common_models';
import {TEST_SERVICE} from '../../../../core/services/test/test_service';
import {TestFilesTab} from './test_files_tab';

class MockTestService {
  getTestFile(
    testId: string,
    jobId: string,
    filePath: string,
  ): Observable<string> {
    return of(`mock content for ${filePath} (test: ${testId}, job: ${jobId})`);
  }
}

@Component({
  standalone: true,
  imports: [TestFilesTab],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-test-files-tab
      [testId]="testId"
      [jobId]="jobId"
      [fileExplorer]="fileExplorer"
    />
  `,
})
class TestHostComponent {
  testId = 'test_123';
  jobId = 'job_456';
  fileExplorer: FileExplorer = {
    cnsPath: '/cns-fake/test-path',
    files: [
      {
        path: 'test_output.txt',
        size: 1200,
        viewable: true,
      },
    ],
  };
}

describe('TestFilesTab Component', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let mockTestService: MockTestService;

  beforeEach(async () => {
    mockTestService = new MockTestService();

    await TestBed.configureTestingModule({
      imports: [TestFilesTab, TestHostComponent, NoopAnimationsModule],
      providers: [{provide: TEST_SERVICE, useValue: mockTestService}],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();
  });

  it('should compile and pass fileExplorer to app-files-tab', () => {
    const filesTab = fixture.debugElement.query(By.css('app-files-tab'));
    expect(filesTab).not.toBeNull();
  });

  it('should delegate getTestFile to TEST_SERVICE', () => {
    const testFilesTab: TestFilesTab = fixture.debugElement.query(
      By.directive(TestFilesTab),
    ).componentInstance;

    spyOn(mockTestService, 'getTestFile').and.callThrough();
    testFilesTab.getFileContent('test_output.txt').subscribe((content) => {
      expect(content).toBe(
        'mock content for test_output.txt (test: test_123, job: job_456)',
      );
    });

    expect(mockTestService.getTestFile).toHaveBeenCalledWith(
      'test_123',
      'job_456',
      'test_output.txt',
    );
  });
});
