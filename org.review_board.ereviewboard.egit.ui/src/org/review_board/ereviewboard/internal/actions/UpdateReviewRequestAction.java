package org.review_board.ereviewboard.internal.actions;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.egit.core.GitProvider;
import org.eclipse.egit.core.project.GitProjectData;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.review_board.ereviewboard.core.ReviewboardCorePlugin;
import org.review_board.ereviewboard.core.ReviewboardDiffMapper;
import org.review_board.ereviewboard.core.ReviewboardRepositoryConnector;
import org.review_board.ereviewboard.core.client.ReviewboardClient;
import org.review_board.ereviewboard.core.exception.ReviewboardException;
import org.review_board.ereviewboard.core.model.Repository;
import org.review_board.ereviewboard.core.model.RepositoryType;
import org.review_board.ereviewboard.core.model.ReviewRequest;
import org.review_board.ereviewboard.egit.ui.internal.Activator;
import org.review_board.ereviewboard.egit.ui.internal.TraceLocation;
import org.review_board.ereviewboard.ui.wizard.PostReviewRequestWizard;
import org.review_board.ereviewboard.ui.editor.ext.TaskDiffAction;

/**
 * @author Robert Munteanu
 */
public class UpdateReviewRequestAction implements TaskDiffAction {

    private TaskRepository repository;
    private int reviewRequestId;
    private Repository codeRepository;
    private Integer diffRevisionId;
    private ReviewboardDiffMapper diffMapper;

    public void init(TaskRepository repository, int reviewRequestId, Repository codeRepository,
    		ReviewboardDiffMapper diffMapper, Integer diffRevisionId) {
        
        this.repository = repository;
        this.reviewRequestId = reviewRequestId;
        this.codeRepository = codeRepository;
        this.diffMapper = diffMapper;
        this.diffRevisionId = diffRevisionId;
    }


    public boolean isEnabled() {
        
        if ( diffRevisionId != null )
            return false; // global action

        return codeRepository != null && codeRepository.getTool() == RepositoryType.Git;
    }

    public IStatus execute(IProgressMonitor monitor) {
        
        monitor.beginTask("Preparing to update the diff", 1);
        
        try {
            
            ReviewboardRepositoryConnector connector = ReviewboardCorePlugin.getDefault().getConnector();
            
            ReviewboardClient client = connector.getClientManager().getClient(repository);
                        
            IWorkspace workspace = ResourcesPlugin.getWorkspace();

            List<IProject> matchingProjects = new ArrayList<IProject>();
            org.eclipse.jgit.lib.Repository repo = null;
            for (IProject project : workspace.getRoot().getProjects()) {

                GitProvider gitProvider = (GitProvider) RepositoryProvider.getProvider(project);

                if (gitProvider == null)
                    continue;

                GitProjectData data = gitProvider.getData();

                RepositoryMapping repositoryMapping = data.getRepositoryMapping(project);

                org.eclipse.jgit.lib.Repository projectGitResource = repositoryMapping.getRepository();
                
                String gitRepositoryName = projectGitResource.getWorkTree().getName();

                if (codeRepository.getName().equals(gitRepositoryName)) {
                	matchingProjects.add(project);
                	repo = projectGitResource;
                }
            }
            
            
            Activator.getDefault().trace(TraceLocation.MAIN, "Matched review request with id " + reviewRequestId + " with project " + matchingProjects);
            
            if (matchingProjects.size() == 0 || repo == null)
                return new Status(IStatus.WARNING, Activator.PLUGIN_ID, "Could not find a matching project for the resources in the review request.");
            
            ReviewRequest reviewRequest = client.getReviewRequest(reviewRequestId, monitor);
            
            IWorkbench wb = PlatformUI.getWorkbench();
            
            IWorkbenchWindow win = wb.getActiveWorkbenchWindow();
            
            if (matchingProjects.size() == 1) {
            	new WizardDialog(win.getShell(), new PostReviewRequestWizard(matchingProjects.get(0), reviewRequest)).open();
            } else {
            	new WizardDialog(win.getShell(), new PostReviewRequestWizard(repo.getRef(repo.getBranch()), repo, reviewRequest)).open();
            }

            return Status.OK_STATUS;
        } catch (ReviewboardException e) {
            return new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Failed updating the diff : " + e.getMessage(), e);
        } catch (IOException e) {
        	return new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Failed updating the diff : " + e.getMessage(), e);
		} finally {
            monitor.done();
        }
    }
}

