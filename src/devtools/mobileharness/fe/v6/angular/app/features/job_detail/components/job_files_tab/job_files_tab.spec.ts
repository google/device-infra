import {ChangeDetectionStrategy, Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';

import {Observable, of} from 'rxjs';

import {FileExplorer} from '../../../../core/models/common_models';
import {
  GetJobFileRequest,
  GetJobFileResponse,
} from '../../../../core/models/job_overview';
import {JOB_SERVICE} from '../../../../core/services/job/job_service';
import {JobFilesTab} from './job_files_tab';

class MockJobService {
  getJobFile(request: GetJobFileRequest): Observable<GetJobFileResponse> {
    return of({
      content: `mock content for ${request.filePath} (job: ${request.jobId})`,
    });
  }
}

@Component({
  standalone: true,
  imports: [JobFilesTab],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-job-files-tab
      [jobId]="jobId"
      [fileExplorer]="fileExplorer"
    />
  `,
})
class TestHostComponent {
  jobId = 'job_123';
  fileExplorer: FileExplorer = {
    cnsPath: '/cns-fake/test-path',
    files: [
      {
        path: 'job_output.txt',
        size: 1200,
        viewable: true,
      },
    ],
  };
}

describe('JobFilesTab Component', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let mockJobService: MockJobService;

  beforeEach(async () => {
    mockJobService = new MockJobService();

    await TestBed.configureTestingModule({
      imports: [JobFilesTab, TestHostComponent, NoopAnimationsModule],
      providers: [{provide: JOB_SERVICE, useValue: mockJobService}],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();
  });

  it('should compile and pass fileExplorer to app-files-tab', () => {
    const filesTab = fixture.debugElement.query(By.css('app-files-tab'));
    expect(filesTab).not.toBeNull();
  });

  it('should delegate getJobFile to JOB_SERVICE', () => {
    const jobFilesTab: JobFilesTab = fixture.debugElement.query(
      By.directive(JobFilesTab),
    ).componentInstance;

    spyOn(mockJobService, 'getJobFile').and.callThrough();
    jobFilesTab.getFileContent('job_output.txt').subscribe((content) => {
      expect(content).toBe('mock content for job_output.txt (job: job_123)');
    });

    expect(mockJobService.getJobFile).toHaveBeenCalledWith({
      jobId: 'job_123',
      filePath: 'job_output.txt',
    });
  });
});
