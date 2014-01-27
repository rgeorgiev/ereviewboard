package org.review_board.ereviewboard.ui.wizard;

/*******************************************************************************
 * Copyright (c) 2011 Robert Munteanu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Robert Munteanu - initial API and implementation
 *******************************************************************************/


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;

/**
 * The <tt>DiffCreator</tt> creates ReviewBoard-compatible diffs
 * 
 * <p>Once specific problem with svn diff is that moved files have an incorrect header.</p>
 * 
 * @see <a href="https://github.com/reviewboard/rbtools/blob/release-0.3.4/rbtools/postreview.py#L1731">post-review handling of svn renames</a>
 * @author Robert Munteanu
 */
public class DiffCreator {

    public byte[] createDiff(Set<ChangedFile> selectedFiles, File rootLocation, Git gitClient) throws IOException, GitAPIException{


    	ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    	List<DiffEntry> changes = new ArrayList<DiffEntry>(selectedFiles.size());
    	
    	for (ChangedFile changedFile : selectedFiles) {
    		changes.add(changedFile.getDiffEntry());
    	}
    		
    	
    	final int INDEX_LENGTH = 40;
    	DiffFormatter diffFormatter = new DiffFormatter(outputStream);
    	diffFormatter.setRepository(gitClient.getRepository());
    	diffFormatter.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
    	diffFormatter.setAbbreviationLength(INDEX_LENGTH);
    	diffFormatter.setDetectRenames(true);

    	diffFormatter.format(changes);
    	diffFormatter.flush();
				
    	return outputStream.toByteArray();
    }
    
}

