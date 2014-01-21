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
package org.review_board.ereviewboard.ui.editor;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.mylyn.reviews.core.model.IComment;
import org.eclipse.mylyn.reviews.core.model.ICommentContainer;
import org.eclipse.mylyn.reviews.core.model.IFileItem;
import org.eclipse.mylyn.reviews.core.model.IFileVersion; ///
import org.eclipse.mylyn.reviews.core.model.ILineLocation;
import org.eclipse.mylyn.reviews.core.model.ILocation;
import org.eclipse.mylyn.reviews.core.model.IReviewItem;
import org.eclipse.mylyn.reviews.core.model.IComment; ///
import org.eclipse.mylyn.reviews.ui.ReviewBehavior;
import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.team.core.history.IFileRevision;
import org.review_board.ereviewboard.core.client.DiffCommentLineMapper;
import org.review_board.ereviewboard.core.client.ReviewboardClient;
import org.review_board.ereviewboard.core.exception.ReviewboardException;
import org.review_board.ereviewboard.core.model.DiffComment;
import org.review_board.ereviewboard.core.model.DiffData;
import org.review_board.ereviewboard.core.model.Review;
import org.review_board.ereviewboard.core.model.reviews.TopicAddedListener;
import org.review_board.ereviewboard.ui.ReviewboardUiPlugin;

/**
 * @author Robert Munteanu
 *
 */
public class ReviewboardReviewBehaviour extends ReviewBehavior {
    
    private IFileItem _fileItem;
    private ReviewboardClient _client;
    private int _diffRevisionId;
    private TopicAddedListener _topicAddedListener;

    public ReviewboardReviewBehaviour(ITask task, IFileItem fileItem, int diffRevisionId, ReviewboardClient client, TopicAddedListener listener) {
        super(task);
        
        _fileItem = fileItem;
        _client = client;
        _diffRevisionId = diffRevisionId;
        _topicAddedListener = listener;
    }
    
    @Override
    public IStatus addComment(IReviewItem fileItem, IComment topic, IProgressMonitor monitor) {
        
        monitor.beginTask("Posting draft comment", 3);
        
        try {
            ILineLocation location = (ILineLocation) topic.getLocations();
            
            int fileId = Integer.parseInt(_fileItem.getId());
            int reviewRequestId = Integer.parseInt(getTask().getTaskId());
            DiffData diffData = _client.getDiffData(reviewRequestId, _diffRevisionId, fileId, monitor);
            monitor.worked(1);
            
            DiffCommentLineMapper lineMapper = new DiffCommentLineMapper(diffData);
            
            int firstLine = findFirstLine(topic, location, lineMapper);
            int numLines = Math.max(location.getRangeMax() - location.getRangeMin(), 1);
            String text = topic.getDescription();
            int fileDiffId = Integer.parseInt(_fileItem.getBase().getId().toString());
            
            DiffComment diffComment = new DiffComment();
            diffComment.setFileId(fileId);
            diffComment.setFirstLine(firstLine);
            diffComment.setNumLines(numLines);
            diffComment.setText(text);
            
            Review draftReview = new Review();
            draftReview.setPublicReview(false);
            
            draftReview = _client.createReview(reviewRequestId, draftReview, monitor);
            monitor.worked(1);
            
            diffComment = _client.createDiffComment(reviewRequestId, draftReview.getId(), fileDiffId, diffComment, monitor);
            monitor.worked(1);
            
            setAuthorFromPostedDiffComment(topic, diffComment);
            
            _topicAddedListener.topicAdded(topic);
            
            return Status.OK_STATUS;
        } catch (ReviewboardException e) {
            return new Status(IStatus.ERROR, ReviewboardUiPlugin.PLUGIN_ID, "Error posting comment: " + e.getMessage(), e);
        } catch ( RuntimeException e ) {
            return new Status(IStatus.ERROR, ReviewboardUiPlugin.PLUGIN_ID, "Error posting comment: " + e.getMessage(), e);
        } finally {
            monitor.done();
        }
    }

    private int findFirstLine(IComment topic, ILineLocation location, DiffCommentLineMapper lineMapper) {
        
        ICommentContainer topicItem = topic.getItem(); //IReviewItem topicItem = topic.getItem();
        IFileVersion baseRevision = _fileItem.getBase(); //
        IFileVersion targetRevision = _fileItem.getTarget(); //
        boolean mapForOldFile = topicItem == baseRevision;
        boolean mapForNewFile = topicItem == targetRevision;
        
        int firstLine;
        if ( mapForOldFile ^ mapForNewFile ) {
            firstLine = mapForOldFile ? lineMapper.getDiffMappingForOldFile(location.getRangeMin())//
                    : lineMapper.getDiffMappingForNewFile((location).getRangeMin());    
        } else {
            // old Reviews UI versions pass a generic 'fileItem' item to the topic which
            // can not be used to determine the revision to annotate
            try {
                firstLine = lineMapper.getDiffMappingForNewFile(location.getRangeMin());
            } catch (RuntimeException e) {
                firstLine = lineMapper.getDiffMappingForOldFile(location.getRangeMin());
            }
        }
        return firstLine;
    }

    ///
    private void setAuthorFromPostedDiffComment(IComment topic, DiffComment diffComment) {
    
        topic.getAuthor().setId(diffComment.getUsername());
        topic.getAuthor().setDisplayName(_client.getClientData().getUser(diffComment.getUsername()).getFullName());
        
        for ( IComment comment : topic.getReplies() ) { //

            comment.getAuthor().setId(diffComment.getUsername());
            comment.getAuthor().setDisplayName(_client.getClientData().getUser(diffComment.getUsername()).getFullName());
        }
    }

    //added method
    @Override
    public IFileRevision getFileRevision(IFileVersion reviewFileVersion) {
        return reviewFileVersion.getFileRevision();
    }

}