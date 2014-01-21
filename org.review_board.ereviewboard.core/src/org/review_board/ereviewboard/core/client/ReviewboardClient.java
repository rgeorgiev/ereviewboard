/*******************************************************************************
 * Copyright (c) 2004 - 2009 Mylyn project committers and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Mylyn project committers, Atlassian, Sven Krzyzak
 *******************************************************************************/
/*******************************************************************************
 * Copyright (c) 2009 Markus Knittig
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * Contributors:
 *     Markus Knittig - adapted Trac, Redmine & Atlassian implementations for
 *                      Review Board
 *******************************************************************************/
package org.review_board.ereviewboard.core.client;

import java.util.Date;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.review_board.ereviewboard.core.exception.ReviewboardException;
import org.review_board.ereviewboard.core.model.*;

/**
 * Interface for Review Board operations.
 *
 * @author Markus Knittig
 */
public interface ReviewboardClient {

    ReviewboardClientData getClientData();

    void refreshRepositorySettings(TaskRepository repository);

    List<ReviewRequest> getReviewRequests(String query, int maxResults, IProgressMonitor monitor) throws ReviewboardException;

    void updateRepositoryData(boolean force, IProgressMonitor monitor) throws ReviewboardException;

    boolean hasRepositoryData();

    IStatus validate(String username, String password, IProgressMonitor monitor);

    List<ReviewRequest> getReviewRequestsChangedSince(Date timestamp, IProgressMonitor monitor) throws ReviewboardException;
    
    byte[] getRawDiff(int reviewRequestId, int diffRevision, IProgressMonitor monitor) throws ReviewboardException;

    byte[] getScreenshot(int reviewRequestId, int screenshotId, IProgressMonitor monitor) throws ReviewboardException;

    ReviewRequest getReviewRequest(int reviewRequestId, IProgressMonitor monitor) throws ReviewboardException;

    List<Diff> loadDiffs(int reviewRequestId, IProgressMonitor monitor) throws ReviewboardException;

    List<FileDiff> getFileDiffs(int reviewRequestId, int diffRevision, IProgressMonitor monitor) throws ReviewboardException;
    
    byte[] getRawFileDiff(int reviewRequestId, int diffRevision, int fileId, IProgressMonitor monitor) throws ReviewboardException;
    
    List<Review> getReviews(int reviewRequestId, IProgressMonitor monitor) throws ReviewboardException;
    
    /**
     * @param reviewRequestId
     * @param monitor
     * @return the draft review for this user, possibly <code>null</code>
     * @throws ReviewboardException
     */
    Review getReviewDraft(final int reviewRequestId, IProgressMonitor monitor) throws ReviewboardException;

    List<ReviewReply> getReviewReplies(final int reviewRequestId, final int reviewId, IProgressMonitor monitor) throws ReviewboardException;
    
    List<DiffComment> readDiffCommentsForReply(int reviewRequestId, int reviewId, int reviewReplyId, IProgressMonitor reviewDiffMonitor) throws ReviewboardException;
    
    int countScreenshotCommentsForReply(int reviewRequestId, int reviewId, int reviewReplyId, IProgressMonitor reviewDiffMonitor) throws ReviewboardException;
    
    List<DiffComment> readDiffCommentsForFileDiff(int reviewRequestId, int diffId, int fileDiffId, IProgressMonitor monitor) throws ReviewboardException;
    
    List<DiffComment> readDiffCommentsFromReview(int reviewRequestId, int reviewId, IProgressMonitor monitor) throws ReviewboardException;

    List<Screenshot> loadScreenshots(int reviewRequestId, IProgressMonitor monitor) throws ReviewboardException;

    List<ScreenshotComment> getScreenshotComments(int reviewRequestId, int id, IProgressMonitor screenshotCommentMonitor) throws ReviewboardException;

    List<Change> getChanges(final int reviewRequestId, IProgressMonitor monitor) throws ReviewboardException;
    
    /**
     * Updates the status of the specified review request
     * 
     * @param reviewRequestId the id of the review request
     * @param status the status to update to, except {@linkplain ReviewRequestStatus#ALL ALL} and {@linkplain ReviewRequestStatus#NONE NONE}
     */
    void updateStatus(int reviewRequestId, ReviewRequestStatus status, IProgressMonitor monitor) throws ReviewboardException;
    
    ReviewRequest createReviewRequest(Repository repository, IProgressMonitor monitor) throws ReviewboardException;
    
    Diff createDiff(int reviewRequestId, String baseDir, byte[] diffContent, IProgressMonitor monitor ) throws ReviewboardException;

    /**
     * Updates the specified review request
     * 
     * <p>The id of the review request must point to an existing review request</p>
     * 
     * @param reviewRequest
     * @param publish true if the changes should be published, otherwise a draft is created
     * @param changedescription the optional description of the changes being made
     * @param monitor
     * @throws ReviewboardException
     */
    ReviewRequestDraft updateReviewRequest(ReviewRequest reviewRequest, boolean publish, String changedescription, IProgressMonitor monitor) throws ReviewboardException;

    Review createReview(int reviewRequestId, Review review, IProgressMonitor monitor) throws ReviewboardException;

    DiffData getDiffData(int reviewRequestId, int diffRevision, int fileId, IProgressMonitor monitor)
            throws ReviewboardException;

    
    DiffComment createDiffComment(int reviewRequestId, int reviewId, int fileDiffId, DiffComment diffComment, IProgressMonitor monitor) throws ReviewboardException;

    void deleteReviewDraft(int reviewRequestId, IProgressMonitor monitor) throws ReviewboardException;
}
