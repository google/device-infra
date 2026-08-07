import {ChangeDetectionStrategy, Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';

import {Observable, of, throwError} from 'rxjs';

import {FileExplorer} from '../../../core/models/common_models';
import {SnackBarService} from '../../../shared/services/snackbar_service';
import * as safeDom from '../../utils/safe_dom';
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
    />
  `,
})
class TestHostComponent {
  id = 'test_resource_123';
  fileExplorer: FileExplorer = {
    cnsPath: '/cns-fake/test-cns-path',
    files: [
      {
        path: '/cns-fake/test-cns-path/folder/file1.txt',
        size: 100,
        viewable: true,
      },
      {
        path: '/cns-fake/test-cns-path/folder/subfolder/file2.txt',
        size: 400,
        viewable: true,
      },
      {
        path: '/cns-fake/test-cns-path/image.png',
        size: 1024,
        viewable: false,
      },
      {
        path: '/cns-fake/test-cns-path/data.bin',
        size: 2048,
        viewable: false,
      },
    ],
  };
  emptyMessage = 'No files found';
  getFileContent = (path: string): Observable<string> =>
    of(`mocked file content for ${path}`);

  lastDownloadedPath = '';

  onDownload(event: {path: string; event: Event}) {
    this.lastDownloadedPath = event.path;
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
    expect(fileNodes.length).toBe(4);
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

    // Directory nodes built for hierarchical paths
    expect(directories.length).toBe(5);
    // Tree shows files under expanded folders
    expect(files.length).toBe(4);
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
      'mocked file content for /cns-fake/test-cns-path/folder/file1.txt',
    );
  });

  it('should reset activeFile via linkedSignal when fileExplorer input changes', () => {
    const txtFile = fixture.componentInstance.fileExplorer.files?.[0]!;
    component.selectFile(txtFile);
    expect(component.activeFile()).toBe(txtFile);

    // Update fileExplorer input
    fixture.componentInstance.fileExplorer = {
      cnsPath: '/cns-fake/new-path',
      files: [{path: 'new_file.log', viewable: true}],
    };
    fixture.detectChanges();

    expect(component.activeFile()).toBeNull();
  });
});
