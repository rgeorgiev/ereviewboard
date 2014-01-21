package org.review_board.ereviewboard.ui.wizard;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.rmi.Remote;
import java.util.HashSet;
import java.util.List;
import java.util.Set;





import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.ui.TasksUi;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;
import org.eclipse.team.core.RepositoryProvider;
import org.review_board.ereviewboard.core.ReviewboardClientManager;
import org.review_board.ereviewboard.core.ReviewboardCorePlugin;
import org.review_board.ereviewboard.core.client.ReviewboardClient;
import org.review_board.ereviewboard.core.exception.ReviewboardException;
import org.review_board.ereviewboard.core.model.Repository;
import org.review_board.ereviewboard.core.model.RepositoryType;
import org.review_board.ereviewboard.core.model.ReviewRequest;
import org.review_board.ereviewboard.egit.ui.internal.Activator;
import org.review_board.ereviewboard.egit.ui.internal.TraceLocation;
import org.eclipse.egit.core.GitProvider;
import org.eclipse.egit.core.RepositoryCache;
import org.eclipse.egit.core.RepositoryUtil;
import org.eclipse.egit.core.project.GitProjectData;
import org.eclipse.egit.core.project.RepositoryMapping;

/**
 * The <tt>DetectLocalChangesPage</tt> shows the local changes
 * 
 * <p>It also allows selection of the resources to be included in the review request.</p>
 * 
 * @author Robert Munteanu
 *
 */
class DetectLocalChangesPage extends WizardPage {

    private final IProject _project;
    private Table _table;
    private final Set<ChangedFile> _selectedFiles = new HashSet<ChangedFile>();
    
    private org.eclipse.jgit.lib.Repository gitRepository;
    private Repository _reviewBoardRepository;
    private TaskRepository _taskRepository;
    private Label _foundRbRepositoryLabel;
    private Label _foundGitRepositoryLabel;
    private final CreateReviewRequestWizardContext _context;
    private boolean _alreadyPopulated;
    private final ReviewRequest _reviewRequest;

    public DetectLocalChangesPage(IProject project, CreateReviewRequestWizardContext context, ReviewRequest reviewRequest) {

        super("Detect local changes", "Detect local changes", null);
        
        setMessage("Select the changes to submit for review. The ReviewBoard instance and the Git repository have been auto-detected.", IMessageProvider.INFORMATION);
        _project = project;
        _context = context;
        _reviewRequest = reviewRequest;
    }

    public void createControl(Composite parent) {

        Composite layout = new Composite(parent, SWT.NONE);
        
        GridLayoutFactory.swtDefaults().numColumns(2).applyTo(layout);
        
        Label rbRepositoryLabel = new Label(layout, SWT.NONE);
        rbRepositoryLabel.setText("Reviewboard repository :");
        
        _foundRbRepositoryLabel = new Label(layout, SWT.NONE);
        _foundRbRepositoryLabel.setText("Unknown");
        
        Label gitRepositoryLabel = new Label(layout, SWT.NONE);
        gitRepositoryLabel.setText("Git repository :");
        
        _foundGitRepositoryLabel = new Label(layout, SWT.NONE);
        _foundGitRepositoryLabel.setText("Unknown");
        
        
        if ( _reviewRequest != null ) {
            Label reviewRequestLabel = new Label(layout, SWT.NONE);
            reviewRequestLabel.setText("Review request :");
            
            Label reviewRequestName = new Label(layout, SWT.NONE);
            reviewRequestName.setText(_reviewRequest.getSummary());
        }

        _table = new Table(layout, SWT.BORDER | SWT.V_SCROLL | SWT.CHECK);
        _table.setLinesVisible (true);
        _table.setHeaderVisible (true);

        GridDataFactory.fillDefaults().span(2, 1).hint(500, 300).grab(true, true).applyTo(_table);
        TableColumn includeColumn = new TableColumn(_table, SWT.NONE);
        includeColumn.setText("Include");
        
        TableColumn typeColumn = new TableColumn(_table, SWT.NONE);
        typeColumn.setText("Change type");

        TableColumn fileColumn = new TableColumn(_table, SWT.NONE);
        fileColumn.setText("File");
        
        
        _table.addListener(SWT.Selection, new Listener() {
            
            public void handleEvent(Event event) {
                
                if ( event.detail == SWT.CHECK ) {
                    
                    ChangedFile eventData = (ChangedFile) event.item.getData();
                    
                    if ( _selectedFiles.contains(eventData) )
                        _selectedFiles.remove(eventData);
                    else
                        _selectedFiles.add(eventData);
                    
                    Activator.getDefault().trace(TraceLocation.MAIN, "Number of selected files is " + _selectedFiles.size()); 
                    
                    if ( _selectedFiles.isEmpty() )
                        setErrorMessage("Please select at least one change to submit for review.");
                    else
                        setErrorMessage(null);
                    
                    getContainer().updateButtons();
                }
            }
        });

        setControl(layout);
    }
    
    @Override
    public void setVisible(boolean visible) {
    
        super.setVisible(visible);
        
        if ( visible )
            populate();
    }
    
    private void populate() {
        
        if ( _alreadyPopulated )
            return;

        try {
            getWizard().getContainer().run(false, true, new IRunnableWithProgress() {
                
                public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
                
                	GitProvider gitProvider = (GitProvider) RepositoryProvider.getProvider(_project);
                    
                	Assert.isNotNull(gitProvider, "No " + GitProvider.class.getSimpleName() + " for " + _project);

                    GitProjectData data = gitProvider.getData();

                    RepositoryMapping repositoryMapping = data.getRepositoryMapping(_project);
                    
                    //the local git repository for the project
                    org.eclipse.jgit.lib.Repository projectGitResourse = repositoryMapping.getRepository();
                    
                    
                    File f;
                    if (!projectGitResourse.isBare())
            			f = projectGitResourse.getWorkTree();
            		else
            			f = projectGitResourse.getDirectory();
                    String repositoryName = f.getName();
                    
                    
                    //needs SSH checking
                    String localRepositorySsh = "";

                    
                    ReviewboardClientManager clientManager = ReviewboardCorePlugin.getDefault().getConnector().getClientManager();
                    ReviewboardClient rbClient = null;
                    Repository reviewBoardRepository = null;
                    TaskRepository taskRepository = null;
                    
                    setGitRepository(projectGitResourse);
                    
                    
                    Activator.getDefault().trace(TraceLocation.MAIN, "Local repository is " + getGitRepository().getWorkTree() + 
					        ", SSH is " + localRepositorySsh);
                    
                    
                    List<String> clientUrls = clientManager.getAllClientUrl();
                    if ( clientUrls.isEmpty() ) {
                        setMessage("No Reviewboard repositories are defined. Please add one using the Task Repositories view.", IMessageProvider.WARNING);
                        return;
                    }
                    
                    boolean hasGitRepos = false;
                    
                    for ( String clientUrl : clientUrls ) {
                        
                        TaskRepository repositoryCandidate = TasksUi.getRepositoryManager().getRepository(ReviewboardCorePlugin.REPOSITORY_KIND, clientUrl);
                        
                        if ( repositoryCandidate == null) {
                            Activator.getDefault().log(IStatus.WARNING, "No repository for clientUrl " + clientUrl +" skipping.");
                            continue;
                        }
                        
                        Activator.getDefault().trace(TraceLocation.MAIN, "Checking repository candidate " + repositoryCandidate.getRepositoryLabel());
                        
                        ReviewboardClient client = clientManager.getClient(repositoryCandidate);
                        
                        Activator.getDefault().trace(TraceLocation.MAIN, "Got reviewboardClient " + client);
                        
                        try {
                            client.updateRepositoryData(false, monitor);
                        } catch (ReviewboardException e) {
                            throw new InvocationTargetException(e, "Failed updating the repository data for " + repositoryCandidate.getRepositoryLabel() + " : " + e.getMessage());
                        } catch (RuntimeException e) {
                            throw new InvocationTargetException(e, "Failed updating the repository data for " + repositoryCandidate.getRepositoryLabel() + " : " + e.getMessage());
                        }
                        
                        Activator.getDefault().trace(TraceLocation.MAIN, "Refreshed repository data , got " + client.getClientData().getRepositories().size() + " repositories.");
                        
             
                        for ( Repository repository : client.getClientData().getRepositories() ) {

                        	Activator.getDefault().trace(TraceLocation.MAIN, "Considering repository of type " + repository.getTool()  + " and path " + repository.getPath());
                            
                            if ( repository.getTool() != RepositoryType.Git )
                                continue;
                            
                           
                            hasGitRepos = true;
                            
                            if ( repositoryName.equals(repository.getName()) ) {
                                reviewBoardRepository = repository;
                                taskRepository = repositoryCandidate;
                                rbClient = client;
                                break;
                            }
                        }
                    }
                    
                    if ( !hasGitRepos ) {
                        setMessage("No Git repositories are defined in the configured ReviewBoard servers. Please add the correspoding repositories to ReviewBoard.");
                        return;
                    }
                    
                    setReviewboardClient(rbClient);
                    setReviewboardRepository(reviewBoardRepository);
                    setTaskRepository(taskRepository);
                    
                    try {
                    
                    	if ( taskRepository != null && reviewBoardRepository != null) {
                    		_foundRbRepositoryLabel.setText(taskRepository.getRepositoryLabel());
                    		_foundRbRepositoryLabel.setToolTipText(taskRepository.getUrl());

                    		_foundGitRepositoryLabel.setText(reviewBoardRepository.getName());
                    		_foundGitRepositoryLabel.setToolTipText(reviewBoardRepository.getPath());

                    	} else {
                    		setErrorMessage("No Git repository defined in ReviewBoard for path " +  getGitRepository().getWorkTree() + ". Please ensure that the repository URL from Eclipse matches the one from ReviewBoard.");
                    		return;
                    	}

                    
                        //LocalResourceStatus status = projectSvnResource.getStatus();
                        //Activator.getDefault().trace(TraceLocation.MAIN, "Git repository status is " + status);
                        //Assert.isNotNull(status, "No status for resource " + projectGitResourse.toString());
                        
                        Git gitClient = new Git(getGitRepository());
                        ChangedFileFinder changedFileFinder = new ChangedFileFinder(gitClient, _project);
                        List<ChangedFile> changedFiles = changedFileFinder.findChangedFiles();
                        
                        Activator.getDefault().trace(TraceLocation.MAIN, "Found " + changedFiles.size() + " changed files.");
                        
                        for ( ChangedFile changedFile : changedFiles ) {
                            
                            TableItem item = new TableItem (_table, SWT.NONE);
                            item.setData(changedFile);
                            item.setText(0, "");
                            item.setText(1, changedFile.getStatusKind().toString());
                            item.setText(2, changedFile.getPathRelativeToProject());
                            
                            item.setChecked(true);
                            
                            _selectedFiles.add(changedFile);
                        }
                        
                        for ( int i = 0 ; i < _table.getColumnCount(); i ++ )
                            _table.getColumn(i).pack();
                        
                        if ( _selectedFiles.isEmpty() ) {
                            setErrorMessage("No changes found in the repository which can be used to create a diff.");
                            return;
                        }
                    } catch (NoWorkTreeException e) {
                        throw new InvocationTargetException(e);
                    } catch (IOException e) {
                        throw new InvocationTargetException(e);
                    } catch (NoHeadException e) {
                    	throw new InvocationTargetException(e);
					} catch (GitAPIException e) {
						throw new InvocationTargetException(e);
					}
                }

            });
        } catch (InvocationTargetException e) {
            setErrorMessage(e.getMessage());
        } catch (InterruptedException e) {
            setErrorMessage(e.getMessage());
        } catch ( RuntimeException e ) {
            setErrorMessage(getErrorMessage());
            Activator.getDefault().log(IStatus.ERROR, e.getMessage(), e);
        } finally {
            _alreadyPopulated = true;
        }
    }
    
    @Override
    public boolean isPageComplete() {
    
        return super.isPageComplete() && getTaskRepository() != null && getReviewBoardRepository() != null && getSelectedFiles().size() > 0 ;
    }
    
    public Set<ChangedFile> getSelectedFiles() {
        
        return _selectedFiles;
    }

    public org.eclipse.jgit.lib.Repository getGitRepository() {

        return gitRepository;
    }
    
    public Repository getReviewBoardRepository() {

        return _reviewBoardRepository;
    }
    
    public TaskRepository getTaskRepository() {

        return _taskRepository;
    }

    void setGitRepository(org.eclipse.jgit.lib.Repository repository) {

        this.gitRepository = repository;
    }

    void setReviewboardClient(ReviewboardClient rbClient) {
        
        _context.setReviewboardClient(rbClient);
    }
    
    void setReviewboardRepository(Repository reviewBoardRepository) {

        _reviewBoardRepository = reviewBoardRepository;
    }
    
    void setTaskRepository(TaskRepository taskRepository) {

        _taskRepository = taskRepository;
    }
    protected RemoteConfig findMatchingRemote(org.eclipse.jgit.lib.Repository repository) throws IOException {
    	Assert.isNotNull(getGitRepository());
    	List<RemoteConfig> remotes;
    	try {
    		remotes = RemoteConfig.getAllRemoteConfigs(getGitRepository().getConfig());
    	} catch (URISyntaxException e) {
    		throw new IOException("Invalid URI in remote configuration", e); //$NON-NLS-1$
    	}
    	for (RemoteConfig remote : remotes) {
    		if (isMatchingRemoteConfig(remote)) {
    			
    			return remote;
    		}
    	}
    	return null;
    }

    private boolean isMatchingRemoteConfig(RemoteConfig remoteConfig) {
    	List<URIish> remoteUris = remoteConfig.getURIs();
    	return !remoteUris.isEmpty() && isMatchingUri(remoteUris.get(0));
    }
    private boolean isMatchingUri(URIish uri) {
    	return _project.getName().equals(calcProjectNameFromUri(uri));
    }
    static String calcProjectNameFromUri(URIish uri) {
    	String path = uri.getPath();
    	path = cleanTrailingDotGit(path);
    	if (isHttpUri(uri)) {
    		path = cleanGerritHttpPrefix(path);
    	}
    	return cleanLeadingSlash(path);
    }
    private static String cleanLeadingSlash(String path) {
    	if (path.startsWith("/")) { //$NON-NLS-1$
    		return path.substring(1);
    	} else {
    		return path;
    	}
    }

    private static String cleanTrailingDotGit(String path) {
    	int dotGitIndex = path.lastIndexOf(".git"); //$NON-NLS-1$
    	if (dotGitIndex >= 0) {
    		return path.substring(0, dotGitIndex);
    	} else {
    		return path;
    	}
    }

    private static boolean isHttpUri(URIish fetchUri) {
    	String scheme = fetchUri.getScheme();
    	return scheme != null && scheme.toLowerCase().startsWith("http"); //$NON-NLS-1$
    }
    private static String cleanGerritHttpPrefix(String path) {
        String httpPathPrefix = "/p/"; //$NON-NLS-1$
        int httpPathPrefixIndex = path.indexOf(httpPathPrefix);
        if (httpPathPrefixIndex >= 0) {
                return path.substring(httpPathPrefixIndex + httpPathPrefix.length());
        } else {
                return path;
        }
}
}
