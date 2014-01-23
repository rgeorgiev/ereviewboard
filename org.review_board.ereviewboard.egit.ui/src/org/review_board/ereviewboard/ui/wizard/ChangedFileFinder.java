package org.review_board.ereviewboard.ui.wizard;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.egit.core.internal.CoreText;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.BranchConfig;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevCommitList;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.review_board.ereviewboard.egit.ui.internal.Activator;
import org.review_board.ereviewboard.egit.ui.internal.TraceLocation;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.NoWorkTreeException;

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
