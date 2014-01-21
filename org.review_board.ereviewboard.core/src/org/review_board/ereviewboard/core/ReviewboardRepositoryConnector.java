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
package org.review_board.ereviewboard.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.*;
import org.eclipse.mylyn.commons.net.Policy;
import org.eclipse.mylyn.tasks.core.*;
import org.eclipse.mylyn.tasks.core.RepositoryResponse.ResponseKind;
import org.eclipse.mylyn.tasks.core.data.*;
import org.eclipse.mylyn.tasks.core.sync.ISynchronizationSession;
import org.review_board.ereviewboard.core.ReviewboardAttributeMapper.Attribute;
import org.review_board.ereviewboard.core.client.ReviewboardAttachmentHandler;
import org.review_board.ereviewboard.core.client.ReviewboardClient;
import org.review_board.ereviewboard.core.exception.ReviewboardException;
import org.review_board.ereviewboard.core.model.*;
import org.review_board.ereviewboard.core.util.ReviewboardUtil;

import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.TreeMultimap;

/**
 * @author Markus Knittig
 *
 */
public class ReviewboardRepositoryConnector extends AbstractRepositoryConnector {
    
    private static final String CLIENT_LABEL = "Reviewboard (supports 1.5 and later)";

    private static final int REVIEW_DIFF_TICKS = 4;
    private static final int SCREENSHOT_COMMENT_TICKS = 2;
    private static final int FILE_DIFF_TICKS = 8;
    
    private final static Pattern REVIEW_REQUEST_ID_FROM_TASK_URL = Pattern
            .compile(ReviewboardConstants.REVIEW_REQUEST_URL + "(\\d+)");

    private ReviewboardClientManager clientManager;

    private TaskRepositoryLocationFactory taskRepositoryLocationFactory;

    public ReviewboardRepositoryConnector() {
        super();

        if (ReviewboardCorePlugin.getDefault() != null) {
            ReviewboardCorePlugin.getDefault().setConnector(this);
        }
    }

    @Override
    public boolean canCreateNewTask(TaskRepository repository) {
        return repository.getConnectorKind().equals(getConnectorKind());
    }

    @Override
    public boolean canCreateTaskFromKey(TaskRepository repository) {
        return true;
    }

    @Override
    public String getConnectorKind() {
        return ReviewboardCorePlugin.REPOSITORY_KIND;
    }

    @Override
    public String getLabel() {
        return CLIENT_LABEL;
    }

    @Override
    public String getRepositoryUrlFromTaskUrl(String taskFullUrl) {
        int index = taskFullUrl.indexOf(ReviewboardConstants.REVIEW_REQUEST_URL);

        if (index > 0) {
            return taskFullUrl.substring(0, index);
        } else {
            return null;
        }
    }

    @Override
    public AbstractTaskAttachmentHandler getTaskAttachmentHandler() {
     
        return new ReviewboardAttachmentHandler(this);
    }
    
    @Override
    public TaskData getTaskData(TaskRepository taskRepository, String taskId,
            IProgressMonitor monitor) throws CoreException {
        try {
            
            long start = System.currentTimeMillis();

            ReviewboardClient client = getClientManager().getClient(taskRepository);
            
            monitor.beginTask("Getting task data", 5 + REVIEW_DIFF_TICKS + SCREENSHOT_COMMENT_TICKS + FILE_DIFF_TICKS);

            try {
                
                int reviewRequestId = Integer.parseInt(taskId);
                
                ReviewRequest reviewRequest = client.getReviewRequest(reviewRequestId, monitor);
                
                Policy.advance(monitor, 1);
                
                TaskData taskData = getTaskDataForReviewRequest(taskRepository, client, reviewRequest, false);
                
                List<Diff> diffs = client.loadDiffs(reviewRequestId, monitor);
                
                Policy.advance(monitor, 1);
                
                List<Screenshot> screenshots = client.loadScreenshots(Integer.parseInt(taskData.getTaskId()), monitor);
                
                Policy.advance(monitor, 1);
                
                Multimap<Integer, DiffComment> fileIdToDiffComments = createTaskDataComments(client, taskData, diffs, screenshots, monitor);
                
                createScreenshotAttachments(client, taskData, taskRepository, screenshots, monitor);
                
                createTaskDataPatchSet(client, taskData, diffs, fileIdToDiffComments, monitor);
                
                return taskData;
            } finally {
                
                double elapsed = ( System.currentTimeMillis() - start) / 1000.0;
                
                ReviewboardCorePlugin.getDefault().trace(TraceLocation.SYNC, "Review request with id  " + taskId + " synchronized in " + NumberFormat.getNumberInstance().format(elapsed) + " seconds.");
                
                monitor.done();
            }

            
        } catch (ReviewboardException e) {
            Status status = new Status(IStatus.ERROR, ReviewboardCorePlugin.PLUGIN_ID, "Failed getting task data for task with id " + taskId + " : " + e.getMessage() , e);
            ReviewboardCorePlugin.getDefault().getLog().log(status);
            throw new CoreException(status);
        }
    }

    /**
     * Advances monitor by two + {@value #REVIEW_DIFF_TICKS} + {@value #SCREENSHOT_COMMENT_TICKS}
     * @param screenshots 
     * @return 
     * 
     */
    private Multimap<Integer, DiffComment> createTaskDataComments(ReviewboardClient client, TaskData taskData, List<Diff> diffs, List<Screenshot> screenshots, IProgressMonitor monitor)
            throws  ReviewboardException {

        SortedMap<Date, ReviewboardCommentMapper> sortedComments = new TreeMap<Date, ReviewboardCommentMapper>();
        
        TreeMultimap<Integer, DiffComment> fileIdIdToDiffComments = TreeMultimap.create(Ordering.natural(), DiffComment.COMPARATOR_ID);
        
        ReviewboardAttributeMapper attributeMapper = (ReviewboardAttributeMapper) taskData.getAttributeMapper();
        
        for (Diff diff : diffs ) {

            ReviewboardCommentMapper comment = new ReviewboardCommentMapper();
            comment.setAuthor(attributeMapper.getRepositoryPerson(taskData.getRoot().getAttribute(Attribute.SUBMITTER.toString())));
            comment.setHeading(Diff.DIFF_REVISION_PREFIX + diff.getRevision());

            sortedComments.put(diff.getTimestamp(), comment);
        }
        
        int reviewRequestId = Integer.parseInt(taskData.getTaskId());
        List<Review> reviews = client.getReviews(reviewRequestId, monitor);
        Policy.advance(monitor, 1);
        
        Review draftReview = client.getReviewDraft(reviewRequestId, monitor);
        if ( draftReview != null )
            reviews.add(draftReview);
        Policy.advance(monitor, 1);

        int shipItCount = 0;
        
        IProgressMonitor reviewDiffMonitor = Policy.subMonitorFor(monitor, REVIEW_DIFF_TICKS);
        reviewDiffMonitor.beginTask("Reading reviews", reviews.size() * 3); // 1 for counting comments, 1 for reading the replies, 1 for counting comments for replies
        
        try {
            for (Review review : reviews) {

                int reviewId = review.getId();
                List<DiffComment> reviewDiffComments = client.readDiffCommentsFromReview(reviewRequestId, reviewId, monitor);
                for ( DiffComment comment : reviewDiffComments ) {
                    comment.setPublic(review.isPublicReview());
                    fileIdIdToDiffComments.put(comment.getFileId(), comment);
                }
                int totalResults  = reviewDiffComments.size(); 
                
                Policy.advance(reviewDiffMonitor, 1);
                
                if ( !review.isPublicReview() ) {
                    // no need to read the reviews or map comments for a draft review
                    Policy.advance(reviewDiffMonitor, 1);
                    continue;
                }

                boolean shipit = review.getShipIt();
                
                if (shipit)
                    shipItCount++;

                ReviewboardCommentMapper comment = new ReviewboardCommentMapper();
                comment.setAuthor(attributeMapper.getRepositoryPerson(attributeMapper.getTaskRepository(), review.getUser()));
                comment.setHeading(review.getShipIt() && !review.hasShipItText() ? "Ship it!" : null);
                comment.setTop(review.getBodyTop());
                comment.setBody(totalResults != 0 ? totalResults + " inline comments" : null);
                comment.setBottom(review.getBodyBottom());

                sortedComments.put(review.getTimestamp(), comment);
                
                List<ReviewReply> replies = client.getReviewReplies(reviewRequestId, reviewId, reviewDiffMonitor);

                Policy.advance(reviewDiffMonitor, 1);
                
                IProgressMonitor counterMonitor = Policy.subMonitorFor(reviewDiffMonitor, 1);
                counterMonitor.beginTask("Reading review replies", replies.size() * 2);

                try {
                    for ( ReviewReply reviewReply : replies ) {
                        
                        ReviewboardCommentMapper replyComment = new ReviewboardCommentMapper();
                        replyComment.setAuthor(attributeMapper.getRepositoryPerson(attributeMapper.getTaskRepository(), reviewReply.getUser()));
                        replyComment.setHeading("In reply to review #" + reviewId + ": ");
                        replyComment.setTop(reviewReply.getBodyTop());
                        List<DiffComment> reviewReplyDiffComments = client.readDiffCommentsForReply(reviewRequestId, reviewId, reviewReply.getId(), reviewDiffMonitor);
                        for ( DiffComment diffComment : reviewReplyDiffComments )
                            fileIdIdToDiffComments.put(diffComment.getFileId(), diffComment);

                        int diffComments = reviewDiffComments.size();
                        Policy.advance(counterMonitor, 1);
                        
                        int screenshotComments = client.countScreenshotCommentsForReply(reviewRequestId, reviewId, reviewReply.getId(), reviewDiffMonitor);
                        Policy.advance(counterMonitor, 1);
                        
                        StringBuilder body = new StringBuilder();
                        if ( diffComments != 0 ) 
                            body.append(diffComments + " inline comments. ");
                        if ( screenshotComments != 0 )
                            body.append(screenshotComments + " screenshot comments.");
                        
                        replyComment.setBody(body.toString());
                        replyComment.setBottom(reviewReply.getBodyBottom());
                        
                        sortedComments.put(reviewReply.getTimestamp(), replyComment);
                    }
                } finally {
                    counterMonitor.done();
                }

            }
        } finally {
            reviewDiffMonitor.done();
        }
        
        IProgressMonitor screenshotCommentMonitor = Policy.subMonitorFor(monitor, SCREENSHOT_COMMENT_TICKS);
        screenshotCommentMonitor.beginTask("Retrieving screeshot comments", screenshots.size());
        
        try {
            
            for ( Screenshot screenshot : screenshots ) {
                
                List<ScreenshotComment> screenshotComments = client.getScreenshotComments(reviewRequestId, screenshot.getId(), screenshotCommentMonitor);
                
                Policy.advance(screenshotCommentMonitor, 1);
                
                for ( ScreenshotComment screenshotComment : screenshotComments ) {
                    
                    ReviewboardCommentMapper screenshotCommentMapper = new ReviewboardCommentMapper();
                    screenshotCommentMapper.setAuthor(attributeMapper.getRepositoryPerson(attributeMapper.getTaskRepository(), screenshotComment.getUsername()));
                    screenshotCommentMapper.setHeading("Comment on screenshot '" + screenshot.getCaption() + "': ");
                    screenshotCommentMapper.setBody(screenshotComment.getText());
                    
                    sortedComments.put(screenshotComment.getTimestamp(), screenshotCommentMapper);
                }
                
            }
            
        } finally {
            screenshotCommentMonitor.done();
        }
        
        TaskAttribute shipItAttribute = taskData.getRoot().createAttribute(Attribute.SHIP_IT.toString());
        shipItAttribute.setValue(String.valueOf(shipItCount));
        shipItAttribute.getMetaData().setLabel(Attribute.SHIP_IT.getDisplayName()).setType(Attribute.SHIP_IT.getAttributeType());
        shipItAttribute.getMetaData().setReadOnly(true).setKind(Attribute.SHIP_IT.getAttributeKind());

        int commentIndex = 1;
        
        for ( Map.Entry<Date, ReviewboardCommentMapper>  entry : sortedComments.entrySet() )
            entry.getValue().applyTo(taskData, commentIndex++, entry.getKey());
        
        return fileIdIdToDiffComments;
    }
    
    /**
     * Advances monitor by {@value #FILE_DIFF_TICKS} ticks
     * @param fileIdToDiffComments 
     */
    private void createTaskDataPatchSet(ReviewboardClient client, TaskData taskData, List<Diff> diffs, Multimap<Integer, DiffComment> fileIdToDiffComments, IProgressMonitor monitor) throws ReviewboardException {
        
        if ( diffs.isEmpty() )
            return;
        
        IProgressMonitor fileDiffMonitor = Policy.subMonitorFor(monitor, FILE_DIFF_TICKS);
        fileDiffMonitor.beginTask("Retrieving file diffs", diffs.size());

        try {
            int reviewRequestId = Integer.parseInt(taskData.getTaskId());
            ReviewboardDiffMapper diffMapper = new ReviewboardDiffMapper(taskData);
            
            for ( Diff diff : diffs ) {
                diffMapper.addDiff(diff, client.getFileDiffs(reviewRequestId, diff.getRevision(), monitor), fileIdToDiffComments);
                Policy.advance(fileDiffMonitor, 1);
            }
        } finally {
            monitor.done();
        }
    }
    
    /**
     * Advances monitor by one
     * @param client 
     */
    private void createScreenshotAttachments(ReviewboardClient client, TaskData taskData, TaskRepository taskRepository, List<Screenshot> screenshots, IProgressMonitor monitor) throws ReviewboardException {
        
        if ( screenshots.isEmpty() )
            return;

        TaskAttribute root = taskData.getRoot();

        int attachmentIndex = 0;
        
        for ( Screenshot screenshot : screenshots ) {
  
            TaskAttribute attribute = root.createAttribute(TaskAttribute.PREFIX_ATTACHMENT + ++ attachmentIndex);
            attribute.setValue(String.valueOf(screenshot.getId()));
            TaskAttachmentMapper mapper = TaskAttachmentMapper.createFrom(attribute);
            mapper.setFileName(screenshot.getFileName());
            mapper.setDescription(screenshot.getCaption());
            mapper.setAttachmentId(Integer.toString(screenshot.getId()));
            mapper.setLength(ReviewboardAttachmentHandler.ATTACHMENT_SIZE_UNKNOWN);
            mapper.setContentType(screenshot.getContentType());
            mapper.setUrl(stripPathFromLocation(taskRepository.getUrl()) + screenshot.getUrl());
            mapper.applyTo(attribute);
        }
        
    }

    private String stripPathFromLocation(String location) throws ReviewboardException {
        
        try {
            URI uri = new URI(location);
            return location.substring(0, location.length() - uri.getPath().length());
        } catch (URISyntaxException e) {
            throw new ReviewboardException("Unable to retrive host from the location", e);
        }
    }
    
    @Override
    public String getTaskIdFromTaskUrl(String taskFullUrl) {
        Matcher matcher = REVIEW_REQUEST_ID_FROM_TASK_URL.matcher(taskFullUrl);

        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    @Override
    public String getTaskUrl(String repositoryUrl, String taskId) {
        return ReviewboardUtil.getReviewRequestUrl(repositoryUrl, taskId);
    }

    @Override
    public boolean hasTaskChanged(TaskRepository taskRepository, ITask task, TaskData taskData) {
        
        Date repositoryDate = getTaskMapping(taskData).getModificationDate();
        Date localDate = task.getModificationDate();
        
        if ( repositoryDate == null )
            return false;

        return !repositoryDate.equals(localDate);
    }
    
    @Override
    public void preSynchronization(ISynchronizationSession event, IProgressMonitor monitor)
            throws CoreException {
        try {
            // No Tasks, don't contact the repository
            if (event.getTasks().isEmpty())
                return;

            TaskRepository repository = event.getTaskRepository();

            // no previous sync, all are stale
            if (repository.getSynchronizationTimeStamp() == null || repository.getSynchronizationTimeStamp().length() == 0) {
                for (ITask task : event.getTasks())
                    event.markStale(task);
                return;
            }
            
            Date lastSyncTimestamp = new Date(Long.parseLong(repository.getSynchronizationTimeStamp()));
            
            ReviewboardClient client = getClientManager().getClient(repository);
            
            List<ReviewRequest> changedReviewRequests = client.getReviewRequestsChangedSince(lastSyncTimestamp, monitor);
            
            if ( changedReviewRequests.isEmpty() )
                return;
            
            Set<Integer> changedReviewRequestIds = new HashSet<Integer>(changedReviewRequests.size());
            for ( ReviewRequest reviewRequest : changedReviewRequests )
                changedReviewRequestIds.add(reviewRequest.getId());
            
            for ( ITask task : event.getTasks() )
                if ( changedReviewRequestIds.contains(Integer.valueOf(task.getTaskId())) )
                    event.markStale(task);
            
            
        } catch (ReviewboardException e) {
            Status status = new Status(IStatus.ERROR, ReviewboardCorePlugin.PLUGIN_ID, "Failed retrieving changed review ids : " + e.getMessage(), e);
            ReviewboardCorePlugin.getDefault().getLog().log(status);
            throw new CoreException(status);
        }
    }

    @Override
    public IStatus performQuery(TaskRepository repository, IRepositoryQuery query,
            TaskDataCollector collector, ISynchronizationSession session, IProgressMonitor monitor) {
        ReviewboardClient client = getClientManager().getClient(repository);

        try {
            client.updateRepositoryData(false, monitor);
                
            List<ReviewRequest> reviewRequests = client.getReviewRequests(query.getUrl(), Integer.parseInt(query.getAttribute("maxResults")), monitor);
            
            for (ReviewRequest reviewRequest : reviewRequests) {
                TaskData taskData = getTaskDataForReviewRequest(repository, client, reviewRequest, true);
                collector.accept(taskData);
            }
        } catch ( ReviewboardException e) {
            // Mylyn does not log the error cause, just decorates the query in the task list
            Status status = new Status(IStatus.ERROR, ReviewboardCorePlugin.PLUGIN_ID, "Failed performing query : " + e.getMessage(), e);
            ReviewboardCorePlugin.getDefault().getLog().log(status);

            return status;
        }

        return Status.OK_STATUS;
    }
    
    private TaskData getTaskDataForReviewRequest(TaskRepository taskRepository,
            ReviewboardClient client, ReviewRequest reviewRequest, boolean partial) {

        String id = String.valueOf(reviewRequest.getId());
        Date dateModified = reviewRequest.getLastUpdated();
        ReviewRequestStatus status = reviewRequest.getStatus();

        TaskData taskData = new TaskData(new ReviewboardAttributeMapper(taskRepository, clientManager.getClient(taskRepository).getClientData()),
                ReviewboardCorePlugin.REPOSITORY_KIND, taskRepository.getUrl(), id);
        taskData.setPartial(partial);

        ReviewboardTaskMapper mapper = new ReviewboardTaskMapper(taskData, true);
        // mapped fields
        mapper.setTaskKey(id);
        mapper.setReporter(reviewRequest.getSubmitter());
        mapper.setCreationDate(reviewRequest.getTimeAdded());
        mapper.setSummary(reviewRequest.getSummary());
        mapper.setDescription(reviewRequest.getDescription());
        mapper.setTaskUrl(ReviewboardUtil.getReviewRequestUrl(taskRepository.getUrl(), id));
        mapper.setStatus(status.name().toLowerCase());
        if ( status != ReviewRequestStatus.PENDING )
            mapper.setCompletionDate(dateModified);
        
        if ( reviewRequest.getRepository() != null ) {
            List<Repository> repositories = client.getClientData().getRepositories();
            for ( Repository repository : repositories )
                if ( repository.getName().equals(reviewRequest.getRepository() ))
                    mapper.setRepository( repository );
        }
        mapper.setBranch(reviewRequest.getBranch());
        mapper.setChangeNum(reviewRequest.getChangeNumber());
        mapper.setPublic(reviewRequest.isPublic());
        mapper.setBugsClosed(reviewRequest.getBugsClosed());
        mapper.setTestingDone(reviewRequest.getTestingDone());
        mapper.setTargetPeople(reviewRequest.getTargetPeople());
        mapper.setTargetGroups(reviewRequest.getTargetGroups());
        
        if ( !partial) {
            // on purpose not set for partial tasks
            mapper.setModificationDate(dateModified);
        }
        
        mapper.addOperations();
        
        mapper.complete();
        
        return taskData;
    }

    @Override
    public void postSynchronization(ISynchronizationSession event, IProgressMonitor monitor) throws CoreException {

        try {
            monitor.beginTask("", 1);
            event.getTaskRepository().setSynchronizationTimeStamp(String.valueOf(getSynchronizationTimestamp(event).getTime()));
        } finally {
            monitor.done();
        }
    }

    private Date getSynchronizationTimestamp(ISynchronizationSession event) {

        Date mostRecent = new Date(0);
        Date mostRecentTimeStamp = null;
        if (event.getTaskRepository().getSynchronizationTimeStamp() == null) {
            mostRecentTimeStamp = mostRecent;
        } else {
            mostRecentTimeStamp = new Date(Long.parseLong(event.getTaskRepository() .getSynchronizationTimeStamp()));
        }
        for (ITask task : event.getChangedTasks()) {
            Date taskModifiedDate = task.getModificationDate();
            if (taskModifiedDate != null && taskModifiedDate.after(mostRecent)) {
                mostRecent = taskModifiedDate;
                mostRecentTimeStamp = task.getModificationDate();
            }
        }
        return mostRecentTimeStamp;
    }

    @Override
    public void updateRepositoryConfiguration(TaskRepository taskRepository,
            IProgressMonitor monitor) throws CoreException {
        
        try {
            clientManager.getClient(taskRepository).updateRepositoryData(true, monitor);
        } catch (ReviewboardException e) {
            throw new CoreException(new Status(IStatus.ERROR, ReviewboardCorePlugin.PLUGIN_ID, "Updating repository configuration failed : " + e.getMessage(), e));
        }
    }

    @Override
    public void updateTaskFromTaskData(TaskRepository taskRepository, ITask task, TaskData taskData) {
        
        TaskMapper scheme = new ReviewboardTaskMapper(taskData);
        scheme.applyTo(task);
        
        task.setUrl(getTaskUrl(taskRepository.getUrl(), task.getTaskId()));
        task.setCompletionDate(scheme.getCompletionDate());
    }

    public synchronized ReviewboardClientManager getClientManager() {
        if (clientManager == null) {
            IPath path = ReviewboardCorePlugin.getDefault().getRepostioryAttributeCachePath();
            clientManager = new ReviewboardClientManager(path.toFile());
        }
        clientManager.setTaskRepositoryLocationFactory(taskRepositoryLocationFactory);

        return clientManager;
    }

    public void stop() {
        if (clientManager != null) {
            clientManager.writeCache();
        }
    }

    public void setTaskRepositoryLocationFactory(TaskRepositoryLocationFactory factory) {
        this.taskRepositoryLocationFactory = factory;
        if (clientManager != null) {
            clientManager.setTaskRepositoryLocationFactory(factory);
        }
    }

    @Override
    public AbstractTaskDataHandler getTaskDataHandler() {
        return new AbstractTaskDataHandler() {
            @Override
            public TaskAttributeMapper getAttributeMapper(TaskRepository taskRepository) {
                return new ReviewboardAttributeMapper(taskRepository, clientManager.getClient(taskRepository).getClientData());
            }

            @Override
            public boolean initializeTaskData(TaskRepository repository, TaskData data,
                    ITaskMapping initializationData, IProgressMonitor monitor) throws CoreException {
                // ignore
                return false;
            }

              @Override
            public RepositoryResponse postTaskData(TaskRepository repository, TaskData taskData,
                    Set<TaskAttribute> oldAttributes, IProgressMonitor monitor)
                    throws CoreException {
                  
                TaskAttribute operation = taskData.getRoot().getMappedAttribute(TaskAttribute.OPERATION);
                
                if ( operation == null )
                    return new RepositoryResponse(ResponseKind.TASK_UPDATED, taskData.getTaskId());
                
                ReviewRequestStatus status;
                
                if ( operation.getValue().equals(ReviewboardTaskMapper.OPERATION_ID_CLOSE) ) {
                    TaskAttribute newStatus = taskData.getRoot().getAttribute(ReviewboardAttributeMapper.Attribute.OPERATION_STATUS.toString());
                    status = ReviewRequestStatus.valueOf(newStatus.getValue());
                } else if ( operation.getValue().equals(ReviewboardTaskMapper.OPERATION_ID_REOPEN)) {
                    status = ReviewRequestStatus.PENDING;
                } else {
                    status = null;
                }
                
                int reviewRequestId = Integer.parseInt(taskData.getTaskId());
                
                try {
                    if ( status != null )
                        clientManager.getClient(repository).updateStatus(reviewRequestId, status, monitor);
                } catch (ReviewboardException e) {
                    throw new CoreException(new Status(Status.ERROR, ReviewboardCorePlugin.PLUGIN_ID, "Failed updating issue status : " + e.getMessage(), e));
                }
                
                if ( operation.getValue().equals(ReviewboardTaskMapper.OPERATION_ID_SHIP_IT) ) {
                    
                    try {
                        clientManager.getClient(repository).createReview(reviewRequestId, Review.newShipItReview(), monitor);
                    } catch ( ReviewboardException e) {
                        throw new CoreException(new Status(Status.ERROR, ReviewboardCorePlugin.PLUGIN_ID, "Failed adding ship-it review : " + e.getMessage(), e));
                    }
                }
                
                return new RepositoryResponse(ResponseKind.TASK_UPDATED, taskData.getTaskId());
            }
        };

    }
    
    @Override
    public ITaskMapping getTaskMapping(TaskData taskData) {
        
        return new ReviewboardTaskMapper(taskData);
    }

    @Override
    public TaskHistory getTaskHistory(TaskRepository repository, ITask task,
            IProgressMonitor monitor) throws CoreException {
        
        try {
            TaskHistory history = new TaskHistory(repository, task);
            
            ReviewboardClient client = getClientManager().getClient(repository);
            
            List<Change> changes = client.getChanges(Integer.valueOf(task.getTaskId()), monitor);
            
            for ( Change change : changes ) {
                
                TaskRevision taskRevision = new TaskRevision(String.valueOf(change.getId()), change.getTimestamp(), repository.createPerson("unknown"));
                
                for ( Change.FieldChange fieldChange : change.getFieldsChanged() )
                    taskRevision.add(new TaskRevision.Change(fieldChange.getField().toString(), fieldChange.getField().toString(), fieldChange.getOld(), fieldChange.getNew()));
                
                history.add(taskRevision);
            }
            
            return history;
        } catch (ReviewboardException e) {
            throw new CoreException(new Status(IStatus.ERROR, ReviewboardCorePlugin.PLUGIN_ID, "Failed retreving history for review with id " + task.getTaskId() + " : " + e.getMessage(), e));
        }
    }
    
    @Override
    public boolean canGetTaskHistory(TaskRepository repository, ITask task) {
        
        return true;
    }
}
