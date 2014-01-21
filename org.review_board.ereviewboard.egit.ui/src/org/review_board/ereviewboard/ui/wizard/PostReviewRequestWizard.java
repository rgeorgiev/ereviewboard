package org.review_board.ereviewboard.ui.wizard;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.egit.core.GitProvider;
import org.eclipse.egit.core.project.GitProjectData;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.patch.Patch;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.ui.TasksUiUtil;
import org.eclipse.team.core.RepositoryProvider;
import org.review_board.ereviewboard.core.client.ReviewboardClient;
import org.review_board.ereviewboard.core.exception.ReviewboardException;
import org.review_board.ereviewboard.core.model.Repository;
import org.review_board.ereviewboard.core.model.ReviewRequest;
import org.review_board.ereviewboard.egit.ui.internal.Activator;
import org.review_board.ereviewboard.egit.ui.internal.TraceLocation;
import org.review_board.ereviewboard.ui.util.ReviewboardImages;


/**
 * @author Robert Munteanu
 *
 */
public class PostReviewRequestWizard extends Wizard {
    
    private final IProject _project;
    private DetectLocalChangesPage _detectLocalChangesPage;
    private PublishReviewRequestPage _publishReviewRequestPage;
    private final CreateReviewRequestWizardContext _context = new CreateReviewRequestWizardContext();

    private UpdateReviewRequestPage _updateReviewRequestPage;

    private ReviewRequest _reviewRequest;

    public PostReviewRequestWizard(IProject project) {

        _project = project;
        setWindowTitle("Create new review request");
        setDefaultPageImageDescriptor(ReviewboardImages.WIZARD_CREATE_REQUEST);
        setNeedsProgressMonitor(true);
    }
    
    public PostReviewRequestWizard(IProject project, ReviewRequest reviewRequest) {
        
        _project = project;
        _reviewRequest = reviewRequest;
        setWindowTitle("Update review request");
        setDefaultPageImageDescriptor(ReviewboardImages.WIZARD_CREATE_REQUEST);
        setNeedsProgressMonitor(true);
    }

    @Override
    public void addPages() {

    	try {
			DetectUncommitedChanges detectUncommited = new DetectUncommitedChanges(_project);
			Set<String> uncommited = detectUncommited.getUncommited();
			if (uncommited.size() > 0 && _reviewRequest == null) {
			    addPage(detectUncommited);
			}
		} catch (NoWorkTreeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (GitAPIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        _detectLocalChangesPage = new DetectLocalChangesPage(_project, _context, _reviewRequest);
        addPage(_detectLocalChangesPage);
        if ( _reviewRequest == null ) {
            _publishReviewRequestPage = new PublishReviewRequestPage(_context);
            addPage(_publishReviewRequestPage);
        } else {
            _updateReviewRequestPage = new UpdateReviewRequestPage();
            addPage(_updateReviewRequestPage);
        }
    }
    
    @Override
    public boolean performFinish() {

        try {
            getContainer().run(false, true, new IRunnableWithProgress() {

                public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
                    
                    monitor.beginTask("Posting review request", 4);

                    SubMonitor sub;
                    
                    try {
                    	org.eclipse.jgit.lib.Repository gitRepository = _detectLocalChangesPage.getGitRepository();
                    	Git gitClient = new Git(gitRepository);
                    	
                        ReviewboardClient rbClient = _context.getReviewboardClient();
                        Repository reviewBoardRepository = _detectLocalChangesPage.getReviewBoardRepository();
                        TaskRepository repository = _detectLocalChangesPage.getTaskRepository();

                        GitProvider gitProvider = (GitProvider) RepositoryProvider.getProvider(_project);
                        
                    	Assert.isNotNull(gitProvider, "No " + GitProvider.class.getSimpleName() + " for " + _project);
                        
                        GitProjectData data = gitProvider.getData();

                        RepositoryMapping repositoryMapping = data.getRepositoryMapping(_project);
                        
                        org.eclipse.jgit.lib.Repository projectGitResource = repositoryMapping.getRepository();
                        
                        sub = SubMonitor.convert(monitor, "Creating patch", 1);
                        
                        DiffCreator diffCreator = new DiffCreator();
                        
                        byte[] diffContent = diffCreator.createDiff(_detectLocalChangesPage.getSelectedFiles(), _project.getLocation().toFile(), gitClient);
                        
                        sub.done();
                        
                        ReviewRequest reviewRequest;

                        if ( _reviewRequest == null ) {
                            sub = SubMonitor.convert(monitor, "Creating initial review request", 1);
                            
                            reviewRequest = rbClient.createReviewRequest(reviewBoardRepository, sub);
                            
                            sub.done();
    
                            Activator.getDefault().trace(TraceLocation.MAIN, "Created review request with id " + reviewRequest.getId());
                        } else {
                            reviewRequest = _reviewRequest;
                        }

                        //url - the whole name: repository root/this, repository root - aka head
//                        String basePath = projectSvnResource.getUrl().toString()
//                                .substring(svnRepository.getRepositoryRoot().toString().length());
                        String basePath = projectGitResource.getIndexFile().toString()
                                .substring(gitRepository.getIndexFile().toString().length());
                        if ( basePath.length() == 0 ) {
                            basePath = "/";
                        }

                        Activator.getDefault().trace(TraceLocation.MAIN, "Detected base path " + basePath);
                        
                        sub = SubMonitor.convert(monitor, "Posting diff patch", 1);
                        
                        rbClient.createDiff(reviewRequest.getId(), basePath, diffContent, monitor);
                        
                        sub.done();

                        Activator.getDefault().trace(TraceLocation.MAIN, "Diff created.");

                        ReviewRequest reviewRequestForUpdate;
                        if ( _reviewRequest == null ) {
                            reviewRequestForUpdate = _publishReviewRequestPage.getReviewRequest();
                            reviewRequestForUpdate.setId(reviewRequest.getId());
                        } else {
                            reviewRequestForUpdate = _reviewRequest;
                        }

                        sub = SubMonitor.convert(monitor, "Publishing review request", 1);
                        
                        String changeDescription = null;
                        if ( _reviewRequest != null ) {
                            changeDescription = _updateReviewRequestPage.getChangeDescription();
                        }
                        
                        rbClient.updateReviewRequest(reviewRequestForUpdate, true, changeDescription, monitor);
                        
                        sub.done();

                        TasksUiUtil.openTask(repository, String.valueOf(reviewRequest.getId()));
                    } catch (IOException e) {
                        throw new InvocationTargetException(e);
                    } catch (ReviewboardException e) {
                        throw new InvocationTargetException(e);
                    } catch (GitAPIException e) {
                    	throw new InvocationTargetException(e);
					} finally {
                        monitor.done();
                    }
                }
            });
        } catch (InvocationTargetException e) {
            ((WizardPage) getContainer().getCurrentPage()).setErrorMessage("Failed creating new review request : " + e.getCause().getMessage());
            e.getCause().printStackTrace();
            return false;
        } catch (InterruptedException e) {
            return false;
        }

        return true;
    }

}

