package org.review_board.ereviewboard.ui.wizard;


import java.io.IOException;
import java.util.Collections;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Assert;
import org.eclipse.egit.core.GitProvider;
import org.eclipse.egit.core.project.GitProjectData;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.ui.TasksUi;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.team.core.RepositoryProvider;
import org.review_board.ereviewboard.core.model.ReviewRequest;
import org.review_board.ereviewboard.egit.ui.internal.Activator;
import org.review_board.ereviewboard.ui.ReviewboardUiPlugin;
import org.review_board.ereviewboard.ui.internal.control.EnhancedAutoCompleteField;
import org.review_board.ereviewboard.ui.internal.control.Proposal;
import org.review_board.ereviewboard.ui.util.UiUtils;

/**
 * @author Robert Munteanu
 *
 */
class PublishReviewRequestPage extends WizardPage {

    private EnhancedAutoCompleteField _toUserComboAutoCompleteField;
    private EnhancedAutoCompleteField _toGroupComboAutoCompleteField;
    private final ReviewRequest reviewRequest = new ReviewRequest();
    private String branchName;
    private String commitFirstLine = "";
    private String commitDescription = "";
    
    private final CreateReviewRequestWizardContext _context;

    public PublishReviewRequestPage(CreateReviewRequestWizardContext context, IProject project, Ref branch, Repository repository) throws IOException {

        super("Publish review request", "Publish review request", null);
        
        setMessage("Fill in the review request details. Description, summary and a target person or a target group are required.", IMessageProvider.INFORMATION);
        
        _context = context;
        
        if (project == null) {
        	branchName = BranchTrackingStatus.of(repository, branch.getName()).getRemoteTrackingBranch();
        	RevWalk walk = new RevWalk(repository);
            RevCommit commit = walk.parseCommit(branch.getObjectId());
        	commitFirstLine = commit.getShortMessage();
        	commitDescription = commit.getFullMessage().substring(commitFirstLine.length()).trim();
        } else {
        	GitProvider gitProvider = (GitProvider) RepositoryProvider.getProvider(project);
            
        	Assert.isNotNull(gitProvider, "No " + GitProvider.class.getSimpleName() + " for " + project);
            
            GitProjectData data = gitProvider.getData();

            RepositoryMapping repositoryMapping = data.getRepositoryMapping(project);
            
            Repository repo = repositoryMapping.getRepository();
            branchName = BranchTrackingStatus.of(repo, repo.getFullBranch()).getRemoteTrackingBranch();
            RevWalk walk = new RevWalk(repo);
            RevCommit commit = walk.parseCommit(repo.getRef(repo.getFullBranch()).getObjectId());
            commitFirstLine = commit.getShortMessage();
        	commitDescription = commit.getFullMessage().substring(commitFirstLine.length()).trim();
            
        }
        branchName = branchName.substring(branchName.lastIndexOf('/') + 1);
    }

    public void createControl(Composite parent) {

        Composite layout = new Composite(parent, SWT.NONE);
        
        GridLayoutFactory.fillDefaults().numColumns(2).applyTo(layout);
        
        newLabel(layout, "Summary:");

        final StyledText summary = UiUtils.newSinglelineText(layout);
        if (ReviewboardUiPlugin.getDefault().getPreferenceStore().getBoolean("guessSummary")) {
        	summary.setText(commitFirstLine);
        	reviewRequest.setSummary(summary.getText());
        }
        summary.addModifyListener(new ModifyListener() {
            
            public void modifyText(ModifyEvent e) {
            
               reviewRequest.setSummary(summary.getText());
               
               getContainer().updateButtons();
            }
        });

        newLabel(layout, "Bugs closed:");
        
        final Text bugsClosed = newText(layout);
        ITask activeTask = TasksUi.getTaskActivityManager().getActiveTask();
        if ( activeTask != null && activeTask.getTaskKey() != null)
            bugsClosed.setText(activeTask.getTaskKey());
        
        bugsClosed.addModifyListener(new ModifyListener() {
            
            public void modifyText(ModifyEvent e) {
                
                reviewRequest.setBugsClosed(Collections.singletonList(bugsClosed.getText()));
                
                getContainer().updateButtons();
            }
        });
        
        newLabel(layout, "Branch:");
        
        final Text branch = newText(layout);
        branch.setText(branchName);
        reviewRequest.setBranch(branch.getText());
        
        branch.addModifyListener(new ModifyListener() {
            
            public void modifyText(ModifyEvent e) {
                
                reviewRequest.setBranch(branch.getText());
                
                getContainer().updateButtons();
            }
        });
        
        newLabel(layout, "Description:");
        
        final StyledText description = UiUtils.newMultilineText(layout);
        if (ReviewboardUiPlugin.getDefault().getPreferenceStore().getBoolean("guessDescription")) {
        	description.setText(commitDescription);
        	reviewRequest.setDescription(description.getText());
        }
        description.addModifyListener(new ModifyListener() {
            
            public void modifyText(ModifyEvent e) {
            
               reviewRequest.setDescription(description.getText());
               
               getContainer().updateButtons();
            }
        });

        newLabel(layout, "Testing done:");
        
        final StyledText testingDone = UiUtils.newMultilineText(layout);
        
        testingDone.addModifyListener(new ModifyListener() {
            
            public void modifyText(ModifyEvent e) {
            
                reviewRequest.setTestingDone(testingDone.getText());
                
                getContainer().updateButtons();
            }
        });
        
        newLabel(layout, "Target user:");
        
        final Text toUserText = newText(layout);
        
        _toUserComboAutoCompleteField = new EnhancedAutoCompleteField(toUserText, new Proposal[0]);
        String targetUser = ReviewboardUiPlugin.getDefault().getPreferenceStore().getString("targetUser");
        if (!targetUser.equals("")) {
        	toUserText.setText(targetUser);
        	reviewRequest.setTargetPeople(Collections.singletonList(toUserText.getText()));
        }
       
        
        toUserText.addModifyListener(new ModifyListener() {
            
            public void modifyText(ModifyEvent e) {
            
                reviewRequest.setTargetPeople(Collections.singletonList(toUserText.getText()));
                
                getContainer().updateButtons();
            }
        });
        
        newLabel(layout, "Target group:");
        
        final Text toGroupText = newText(layout);
        
        _toGroupComboAutoCompleteField = new EnhancedAutoCompleteField(toGroupText, new Proposal[0]);
        String targetGroup = ReviewboardUiPlugin.getDefault().getPreferenceStore().getString("targetGroup");
        if (!targetGroup.equals("")) {
        	toUserText.setText(targetGroup);
        	reviewRequest.setTargetGroups(Collections.singletonList(toGroupText.getText()));
        }
        toGroupText.addModifyListener(new ModifyListener() {
            
            public void modifyText(ModifyEvent e) {
                
                reviewRequest.setTargetGroups(Collections.singletonList(toGroupText.getText()));
                
                getContainer().updateButtons();
            }
        });
        
        setControl(layout);
    }

    private void newLabel(Composite layout, String text) {

        Label descriptionLabel = new Label(layout, SWT.NONE );
        descriptionLabel.setText(text);
        GridDataFactory.swtDefaults().align(SWT.BEGINNING, SWT.BEGINNING).applyTo(descriptionLabel);
    }
    
    private Text newText(Composite layout) {
        
        final Text toUserText = new Text(layout, SWT.BORDER | SWT.SINGLE);
        GridDataFactory.swtDefaults().hint(UiUtils.FULL_TEXT_WIDTH, SWT.DEFAULT).applyTo(toUserText);
        return toUserText;
    }
    
    @Override
    public boolean isPageComplete() {
    
        return super.isPageComplete() && checkValid();
    }
    
    private boolean checkValid() {

        if (reviewRequest.getSummary() == null || reviewRequest.getSummary().length() == 0 ) {
            return false;
        }
        
        if ( reviewRequest.getDescription() == null || reviewRequest.getDescription().length() == 0 ) {
            return false;
        }
        
        if ( reviewRequest.getTargetGroups().isEmpty() && reviewRequest.getTargetPeople().isEmpty()) {
            return false;
        }
        
        return true;
            
    }

    @Override
    public void setVisible(boolean visible) {
    
        if ( visible) {
            _toUserComboAutoCompleteField.setProposals(UiUtils.adaptUsers(_context.getReviewboardClient().getClientData().getUsers()));
            _toGroupComboAutoCompleteField.setProposals(UiUtils.adaptGroups(_context.getReviewboardClient().getClientData().getGroups()));
        }
        
        super.setVisible(visible);
    }

    public ReviewRequest getReviewRequest() {

        return reviewRequest;
    }
}
