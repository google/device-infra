import {ChangeDetectionStrategy, Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';

import {Observable, of} from 'rxjs';

import {FileExplorer} from '../../../core/models/common_models';
import {SnackBarService} from '../../../shared/services/snackbar_service';
import {FilesTab} from './files_tab';

@Component({
  standalone: true,
  imports: [FilesTab],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-files-tab
      [id]="id"
      [fileExplorer]="fileExplorer"
      [getFileContent]="getFileContent"
      [emptyMessage]="emptyMessage"
      (download)="onDownload($event)"
      (downloadAll)="onDownloadAll()"
    />
  `,
})
class TestHostComponent {
  id = 'test_resource_123';
  fileExplorer: FileExplorer = {
    cnsPath: '/cns-fake/test-cns-path',
    files: [
      {path: 'folder/file1.txt', size: 100, type: 'text/plain'},
      {path: 'folder/subfolder/file2.txt', size: 400, type: 'text/plain'},
      {path: 'image.png', size: 1024, type: 'image/png'},
    ],
  };
  emptyMessage = 'No files found';
  getFileContent = (path: string): Observable<string> =>
    of(`mocked file content for ${path}`);

  lastDownloadedPath = '';
  downloadAllCalled = false;

  onDownload(event: {path: string; event: Event}) {
    this.lastDownloadedPath = event.path;
  }

  onDownloadAll() {
    this.downloadAllCalled = true;
  }
}

describe('FilesTab Component', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let component: FilesTab;
  let snackBarSpy: jasmine.SpyObj<SnackBarService>;

  beforeEach(async () => {
    snackBarSpy = jasmine.createSpyObj('SnackBarService', [
      'showSuccess',
      'showError',
    ]);

    await TestBed.configureTestingModule({
      imports: [FilesTab, TestHostComponent, NoopAnimationsModule],
      providers: [{provide: SnackBarService, useValue: snackBarSpy}],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();
    component = fixture.debugElement.query(
      By.directive(FilesTab),
    ).componentInstance;
  });

  it('should compile and display cnsPath', () => {
    expect(component).toBeTruthy();
    const cnsText = fixture.debugElement.query(By.css('.cns-path-text'));
    expect(cnsText.nativeElement.textContent.trim()).toBe(
      '/cns-fake/test-cns-path',
    );
  });

  it('should render correct empty state message when there are no files', () => {
    fixture.componentInstance.fileExplorer = {files: []};
    fixture.detectChanges();

    const emptyText = fixture.debugElement.query(By.css('.files-empty-text'));
    expect(emptyText.nativeElement.textContent.trim()).toBe('No files found');
  });

  it('should default to flat view and list files in flat list', () => {
    const fileNodes = fixture.debugElement.queryAll(By.css('.file-node'));
    expect(fileNodes.length).toBe(3);
    const textNode = fileNodes[0].query(By.css('.file-name-text'));
    expect(textNode.nativeElement.textContent.trim()).toContain(
      'folder/file1.txt',
    );
  });

  it('should support searching and filter files dynamically', () => {
    component.searchTerm.set('image');
    fixture.detectChanges();

    const fileNodes = fixture.debugElement.queryAll(By.css('.file-node'));
    expect(fileNodes.length).toBe(1);
    const textNode = fileNodes[0].query(By.css('.file-name-text'));
    expect(textNode.nativeElement.textContent.trim()).toContain('image.png');
  });

  it('should change view mode to tree and build hierarchy', () => {
    component.setViewMode('tree');
    fixture.detectChanges();

    const directories = fixture.debugElement.queryAll(
      By.css('.directory-node'),
    );
    const files = fixture.debugElement.queryAll(By.css('.file-node'));

    // 'folder' and 'subfolder' directories are built
    expect(directories.length).toBe(2);
    // Tree shows files under expanded folders
    expect(files.length).toBe(3);
  });

  it('should handle preview content loading', async () => {
    const txtFile = fixture.componentInstance.fileExplorer.files?.[0];
    if (!txtFile) {
      fail('Expected fileExplorer.files[0] to be defined');
      return;
    }
    component.selectFile(txtFile);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const previewPre = fixture.debugElement.query(By.css('.preview-text-pre'));
    expect(previewPre).not.toBeNull();
    expect(previewPre.nativeElement.textContent.trim()).toBe(
      'mocked file content for folder/file1.txt',
    );
  });

  it('should emit download events on click', () => {
    const downloadBtn = fixture.debugElement.query(
      By.css('.download-file-btn'),
    );
    downloadBtn.nativeElement.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.lastDownloadedPath).toBe(
      'folder/file1.txt',
    );
  });

  it('should emit downloadAll event on header button click', () => {
    const downloadAllBtn = fixture.debugElement.query(
      By.css('.download-all-btn'),
    );
    downloadAllBtn.nativeElement.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.downloadAllCalled).toBeTrue();
  });
});
