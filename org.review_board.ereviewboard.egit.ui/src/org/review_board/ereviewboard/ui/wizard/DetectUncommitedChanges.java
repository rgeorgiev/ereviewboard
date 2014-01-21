package org.review_board.ereviewboard.ui.wizard;


import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Assert;
import org.eclipse.egit.core.GitProvider;
import org.eclipse.egit.core.project.GitProjectData;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.team.core.RepositoryProvider;
import org.review_board.ereviewboard.ui.util.UiUtils;

public class DetectUncommitedChanges extends WizardPage{

	private Table _table;
	private IProject _project;
	private Set<String> uncommited;
	
	public DetectUncommitedChanges(IProject project) throws NoWorkTreeException, GitAPIException {
		super("Detecting uncommited files","Detecting uncommited files", null);
        setMessage("There are files that have not been commited."
        		, IMessageProvider.INFORMATION);
		_project = project;
		uncommited = getUncommitedFiles();
	}
	
	public Set<String> getUncommited() {
		return uncommited;
	}
	
	private Set<String> getUncommitedFiles() throws NoWorkTreeException, GitAPIException {

		GitProvider gitProvider = (GitProvider) RepositoryProvider.getProvider(_project);
		Assert.isNotNull(gitProvider, "No " + GitProvider.class.getSimpleName() + " for " + _project);
		GitProjectData data = gitProvider.getData();

		RepositoryMapping repositoryMapping = data.getRepositoryMapping(_project);

		org.eclipse.jgit.lib.Repository repository = repositoryMapping.getRepository();

		Git client = new Git(repository);

		String projectPath = _project.getFullPath().toString();
		if (projectPath.startsWith("/")) {
			projectPath = projectPath.substring(1);
		}
		if (!projectPath.endsWith("/")) {
			projectPath = projectPath + "/";
		}
		Status status = client.status().addPath(projectPath).call();

		return status.getUncommittedChanges();
	}

	public void createControl(Composite parent) {
		Composite layout = new Composite(parent, SWT.NONE);
		GridLayoutFactory.swtDefaults().applyTo(layout);

		_table = new Table(layout, SWT.BORDER | SWT.V_SCROLL);
		_table.setLinesVisible (true);
		_table.setHeaderVisible (true);
		
		GridDataFactory.fillDefaults().hint(500, 300).grab(true, true).applyTo(_table);
		
		TableColumn fileColumn = new TableColumn(_table, SWT.NONE);
		fileColumn.setText("File");
		
		
		for ( String file : uncommited ) {

			TableItem item = new TableItem (_table, SWT.NONE);
			item.setText(0, file);
		}
		for ( int i = 0 ; i < _table.getColumnCount(); i ++ )
            _table.getColumn(i).pack();
		
		setControl(layout);
	}
	 @Override
	    public boolean isPageComplete() {
	    
	        return super.isPageComplete() && getUncommited().size() == 0 ;
	    }

}
