package org.review_board.ereviewboard.ui.wizard;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.core.GitProvider;
import org.eclipse.egit.core.project.GitProjectData;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.patch.Patch;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.ui.TasksUiUtil;
import org.eclipse.team.core.RepositoryProvider;
import org.review_board.ereviewboard.core.client.ReviewboardClient;
import org.review_board.ereviewboard.core.exception.ReviewboardException;
import org.review_board.ereviewboard.core.model.Repository;
import org.review_board.ereviewboard.core.model.ReviewRequest;
import org.review_board.ereviewboard.core.model.ReviewRequestDraft;
import org.review_board.ereviewboard.egit.ui.internal.Activator;
import org.review_board.ereviewboard.egit.ui.internal.TraceLocation;
import org.review_board.ereviewboard.ui.util.ReviewboardImages;


/**
 * @author Robert Munteanu
 *
 */
public class PostReviewRequestWizard extends Wizard {
    
    private IProject _project = null;
    private Ref _branch = null;
    private org.eclipse.jgit.lib.Repository _gitRepository;
    private DetectLocalChangesPage _detectLocalChangesPage;
    private PublishReviewRequestPage _publishReviewRequestPage;
    private final CreateReviewRequestWizardContext _context = new CreateReviewRequestWizardContext();

    private UpdateReviewRequestPage _updateReviewRequestPage;

    private ReviewRequest _reviewRequest;

    //for 1 project
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
    public PostReviewRequestWizard(Ref branch, org.eclipse.jgit.lib.Repository repository, ReviewRequest reviewRequest) {
        
    	_branch = branch;
    	_gitRepository = repository;
    	_reviewRequest = reviewRequest;
        setWindowTitle("Update review request");
        setDefaultPageImageDescriptor(ReviewboardImages.WIZARD_CREATE_REQUEST);
        setNeedsProgressMonitor(true);
    }

    //for 1 branch
    public PostReviewRequestWizard(Ref branch, org.eclipse.jgit.lib.Repository repository) {
    	_branch = branch;
    	_gitRepository = repository;
    	setWindowTitle("Create new review request");
        setDefaultPageImageDescriptor(ReviewboardImages.WIZARD_CREATE_REQUEST);
        setNeedsProgressMonitor(true);
    }
    @Override
    public void addPages() {

    	try {
			DetectUncommitedChanges detectUncommited = new DetectUncommitedChanges(_project, _branch, _gitRepository);

			Set<String> uncommited = detectUncommited.getUncommited();
			if (uncommited.size() > 0) {
			    addPage(detectUncommited);
			}
			
	    	_detectLocalChangesPage = new DetectLocalChangesPage(_project, _branch, _gitRepository, _context, _reviewRequest);
	        addPage(_detectLocalChangesPage);
	        
	        if ( _reviewRequest == null ) {
	            _publishReviewRequestPage = new PublishReviewRequestPage(_context, _project, _branch, _gitRepository);
	            addPage(_publishReviewRequestPage);
	        } else {
	            _updateReviewRequestPage = new UpdateReviewRequestPage();
	            addPage(_updateReviewRequestPage);
	        }
		} catch (NoWorkTreeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (GitAPIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    	
    }
    
    @Override
    public boolean performFinish() {

        try {
            getContainer().run(false, true, new IRunnableWithProgress() {

                public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
                    
                    monitor.beginTask("Posting review request", 5);

                    SubMonitor sub;
                    
                    try {
                    	org.eclipse.jgit.lib.Repository gitRepository = _detectLocalChangesPage.getGitRepository();
                    	Git gitClient = new Git(gitRepository);
                    	
                        ReviewboardClient rbClient = _context.getReviewboardClient();
                        Repository reviewBoardRepository = _detectLocalChangesPage.getReviewBoardRepository();
                        TaskRepository repository = _detectLocalChangesPage.getTaskRepository();

                        org.eclipse.jgit.lib.Repository projectGitResource;
                        if (_project == null) {
                        	projectGitResource = _gitRepository;
                        } else {
                        	GitProvider gitProvider = (GitProvider) RepositoryProvider.getProvider(_project);
                            
                        	Assert.isNotNull(gitProvider, "No " + GitProvider.class.getSimpleName() + " for " + _project);
                            
                            GitProjectData data = gitProvider.getData();

                            RepositoryMapping repositoryMapping = data.getRepositoryMapping(_project);
                            
                            projectGitResource = repositoryMapping.getRepository();
                        }
                        
                        
                        sub = SubMonitor.convert(monitor, "Creating patch", 1);
                        
                        DiffCreator diffCreator = new DiffCreator();
                        
                        byte[] diffContent;
                        if (_project == null) {
                        	diffContent = diffCreator.createDiff(_detectLocalChangesPage.getSelectedFiles(), projectGitResource.getDirectory(), gitClient);
                        } else {
                        	diffContent = diffCreator.createDiff(_detectLocalChangesPage.getSelectedFiles(), _project.getLocation().toFile(), gitClient);
                        }
                        
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
                        	
                        	//sub = SubMonitor.convert(monitor, "Amending commit message", 1);
                        	
                            reviewRequestForUpdate = _publishReviewRequestPage.getReviewRequest();
                            reviewRequestForUpdate.setId(reviewRequest.getId());
                            
                            RevWalk walk = new RevWalk(projectGitResource);
                            RevCommit commit = walk.parseCommit(projectGitResource.getRef(projectGitResource.getFullBranch()).getObjectId());
                            String commitMessage = commit.getFullMessage();
                            
                            String idPart = "/r/" + reviewRequestForUpdate.getId();
                            String reviewURL = "Review URL: " + repository.getUrl() + idPart;
                            
                            commitMessage = commitMessage.concat("\n"+reviewURL);
                            
                            final CommitCommand command = gitClient.commit().setAmend(true).setAll(true).setMessage(commitMessage);
                            Job commitJob = new Job("Amending commit message") {
								
								@Override
								protected IStatus run(IProgressMonitor monitor) {
									try {
										command.call();
									} catch (NoHeadException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									} catch (NoMessageException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									} catch (UnmergedPathsException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									} catch (ConcurrentRefUpdateException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									} catch (WrongRepositoryStateException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									} catch (GitAPIException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
									return Status.OK_STATUS;
								}
							};
                            commitJob.setProgressGroup(monitor, 1);
                            commitJob.schedule();
                           // sub.done();
                            Activator.getDefault().trace(TraceLocation.MAIN, "Commit ammended");
                        } else {
                            reviewRequestForUpdate = _reviewRequest;
                        }

                        sub = SubMonitor.convert(monitor, "Publishing review request", 1);
                        
                        String changeDescription = null;
                        if ( _reviewRequest != null ) {
                            changeDescription = _updateReviewRequestPage.getChangeDescription();
                        }
                        
                        
                        rbClient.updateReviewRequest(reviewRequestForUpdate, false, changeDescription, monitor);
                        
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

