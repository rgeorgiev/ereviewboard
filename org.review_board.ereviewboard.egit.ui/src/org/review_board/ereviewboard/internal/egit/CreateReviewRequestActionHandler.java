package org.review_board.ereviewboard.internal.egit;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.review_board.ereviewboard.ui.wizard.PostReviewRequestWizard;
import org.eclipse.egit.ui.internal.repository.tree.RepositoryTreeNode;

public class CreateReviewRequestActionHandler extends AbstractHandler{

	
	private Repository repository;

	public Object execute(ExecutionEvent event)
			throws ExecutionException {

		TreeSelection selection = (TreeSelection) HandlerUtil.getCurrentSelection(event);
		TreePath path = selection.getPaths()[0];

		RepositoryTreeNode<FileRepository> treeNote = (RepositoryTreeNode<FileRepository>) path.getLastSegment();
		Ref branch = (Ref) treeNote.getObject();
		Repository repository = (Repository) treeNote.getRepository();
		
		IWorkbench wb = PlatformUI.getWorkbench();
		IWorkbenchWindow win = wb.getActiveWorkbenchWindow();

		new WizardDialog(win.getShell(), new PostReviewRequestWizard(branch, repository)).open();

		return null;
	}



}
