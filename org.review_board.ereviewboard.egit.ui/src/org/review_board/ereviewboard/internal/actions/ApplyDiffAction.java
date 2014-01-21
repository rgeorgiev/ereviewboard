package org.review_board.ereviewboard.internal.actions;


import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.patch.ApplyPatchOperation;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.review_board.ereviewboard.core.ReviewboardCorePlugin;
import org.review_board.ereviewboard.core.ReviewboardDiffMapper;
import org.review_board.ereviewboard.core.ReviewboardRepositoryConnector;
import org.review_board.ereviewboard.core.client.ReviewboardClient;
import org.review_board.ereviewboard.core.exception.ReviewboardException;
import org.review_board.ereviewboard.core.model.Repository;
import org.review_board.ereviewboard.core.model.RepositoryType;
import org.review_board.ereviewboard.core.util.ByteArrayStorage;
import org.review_board.ereviewboard.egit.ui.internal.Activator;
import org.review_board.ereviewboard.egit.ui.internal.TraceLocation;
import org.review_board.ereviewboard.ui.editor.ext.TaskDiffAction;

/**
 * @author Robert Munteanu
 */
public class ApplyDiffAction implements TaskDiffAction {

    private ReviewboardToGitMapper reviewboardToGitMapper = new ReviewboardToGitMapper();
    private TaskRepository repository;
    private int reviewRequestId;
    private Repository codeRepository;
    private Integer diffRevisionId;
    private ReviewboardDiffMapper diffMapper;

    public void init(TaskRepository repository, int reviewRequestId, Repository codeRepository, ReviewboardDiffMapper diffMapper, Integer diffRevisionId) {
        
        this.repository = repository;
        this.reviewRequestId = reviewRequestId;
        this.codeRepository = codeRepository;
        this.diffMapper = diffMapper;
        this.diffRevisionId = diffRevisionId;
    }


    public boolean isEnabled() {
        
        return diffRevisionId != null && codeRepository != null && codeRepository.getTool() == RepositoryType.Git;
    }

    public IStatus execute(IProgressMonitor monitor) {
        
        monitor.beginTask("Preparing to download the diff", 1);
        
        try {
            
            ReviewboardRepositoryConnector connector = ReviewboardCorePlugin.getDefault().getConnector();
            
            ReviewboardClient client = connector.getClientManager().getClient(repository);

            IProject matchingProject = reviewboardToGitMapper.findProjectForRepository(codeRepository, repository, diffMapper);
            
            Activator.getDefault().trace(TraceLocation.MAIN, "Matched review request with id " + reviewRequestId + " with project " + matchingProject);
            
            if ( matchingProject == null )
                return new Status(IStatus.WARNING, Activator.PLUGIN_ID, "Could not find a matching project for the resources in the review request.");
            
            byte[] rawDiff = client.getRawDiff(reviewRequestId, diffRevisionId, monitor);
            monitor.worked(1);
            
            ByteArrayStorage storage = new ByteArrayStorage(rawDiff);
            
            ApplyPatchOperation applyPatch = new ApplyPatchOperation(null, storage, matchingProject, new CompareConfiguration());
            
            applyPatch.openWizard();

            return Status.OK_STATUS;
        } catch (ReviewboardException e) {
            return new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Failed updating the diff : " + e.getMessage(), e);
        } finally {
            monitor.done();
        }
    }
}
