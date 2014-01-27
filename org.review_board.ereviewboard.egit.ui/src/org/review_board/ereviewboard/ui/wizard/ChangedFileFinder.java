package org.review_board.ereviewboard.ui.wizard;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.resources.IProject;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;

/**
 * Finds local changes for a specified <tt>location</tt>
 * 
 * <p>
 * Does not handle {@link SVNStatusKind#UNVERSIONED unversioned} files.
 * </p>
 * 
 * @author Robert Munteanu
 * 
 */
public class ChangedFileFinder {

	Repository repository;
	IProject project;
	Ref branch;

	public ChangedFileFinder(Repository repository, IProject project) throws IOException {

		this.repository = repository;
		this.project = project;
	}

	public ChangedFileFinder(Repository repository, Ref branch) throws IOException {

		this.repository = repository;
		this.branch = branch;
	}
	public List<ChangedFile> findChangedFiles() throws IOException, NoHeadException, GitAPIException{

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		DiffFormatter diffFormatter = new DiffFormatter(outputStream);
		diffFormatter.setRepository(repository);

		Ref currentBranch;
		if (branch == null) {
			currentBranch = repository.getRef(repository.getFullBranch());
		} else {
			currentBranch = branch;
		}
		 

		BranchTrackingStatus btStatus = BranchTrackingStatus.of(repository, currentBranch.getName());
		
		Ref originBranch = repository.getRef(btStatus.getRemoteTrackingBranch());

		List<DiffEntry> diffs = new ArrayList<DiffEntry>();
		diffs = diffFormatter.scan(originBranch.getObjectId(), currentBranch.getObjectId());
		List<ChangedFile> changedFiles = new ArrayList<ChangedFile>();
		
		if (project == null) {
			for (DiffEntry diff : diffs) {
				if (diff.getChangeType() == DiffEntry.ChangeType.DELETE) {
					changedFiles.add(new ChangedFile(diff, diff.getChangeType(), diff.getOldPath()));
				} else {
					changedFiles.add(new ChangedFile(diff, diff.getChangeType(), diff.getNewPath()));
				}
			}
		} else {
			//project path needs to be in projectName/ format
			String projectPath = project.getFullPath().toString();
			if (projectPath.startsWith("/")) {
				projectPath = projectPath.substring(1);
			}
			if (!projectPath.endsWith("/")) {
				projectPath = projectPath + "/";
			}
			
			for (DiffEntry diff : diffs) {
				if (diff.getChangeType() == DiffEntry.ChangeType.DELETE && diff.getOldPath().contains(projectPath)) {
					changedFiles.add(new ChangedFile(diff, diff.getChangeType(), diff.getOldPath()));
				} else if (diff.getNewPath().contains(projectPath)){
					changedFiles.add(new ChangedFile(diff, diff.getChangeType(), diff.getNewPath()));
				}
			}
			
		}
		


		return changedFiles;

	}
}
