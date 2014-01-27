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
package org.review_board.ereviewboard.ui.wizard;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.ui.TasksUi;
import org.eclipse.mylyn.tasks.ui.wizards.AbstractRepositorySettingsPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.review_board.ereviewboard.core.ReviewboardCorePlugin;
import org.review_board.ereviewboard.core.ReviewboardRepositoryConnector;
import org.review_board.ereviewboard.core.ReviewboardRepositoryMapper;
import org.review_board.ereviewboard.core.client.ReviewboardClient;
import org.review_board.ereviewboard.ui.ReviewboardUiPlugin;

/**
 * @author Markus Knittig
 *
 */
public class ReviewboardRepositorySettingsPage extends AbstractRepositorySettingsPage {

    private static final String TITLE = "Reviewboard Repository Settings";

    private static final String DESCRIPTION = "Example: reviews.your-domain.org";

    private String checkedUrl = null;

    private boolean authenticated;
    
    private String username = "";
    private String password = "";

    public ReviewboardRepositorySettingsPage(TaskRepository taskRepository) {
        super(TITLE, DESCRIPTION, taskRepository);

        setNeedsAnonymousLogin(false);
        setNeedsEncoding(false);
        setNeedsTimeZone(false);
        setNeedsValidation(true);
        setNeedsHttpAuth(true);
        setNeedsAdvanced(false);
    }

    @Override
    public void createControl(Composite parent) {

        super.createControl(parent);

        createDefaultSettings(super.compositeContainer);

        checkedUrl = getRepositoryUrl();
     
    }
    
    @Override
    public boolean isPageComplete() {
        return super.isPageComplete() && checkedUrl != null
                && checkedUrl.equals(getRepositoryUrl())
                && username.equals(getUserName())
                && password.equals(getPassword())
                && authenticated;
    }

    @Override
    protected void createAdditionalControls(Composite parent) {
    }

    @Override
    public String getConnectorKind() {
        return ReviewboardCorePlugin.REPOSITORY_KIND;
    }

    @Override
    protected Validator getValidator(final TaskRepository repository) {
        username = getUserName();
        password = getPassword();

        return new Validator() {
            @Override
            public void run(IProgressMonitor monitor) throws CoreException {
                authenticated = false;

                ReviewboardRepositorySettingsPage.this.checkedUrl = repository.getRepositoryUrl();

                ReviewboardRepositoryConnector connector = (ReviewboardRepositoryConnector) TasksUi
                        .getRepositoryManager().getRepositoryConnector(
                        ReviewboardCorePlugin.REPOSITORY_KIND);

                ReviewboardClient client = connector.getClientManager().getClient(repository);
                
                IStatus status = client.validate(username, password, monitor);
                
                if (!status.isOK())
                    throw new CoreException(status);

                authenticated = true;
            }
        };
    }

    @Override
    protected boolean isValidUrl(String url) {
        if ((url.startsWith(URL_PREFIX_HTTPS) || url.startsWith(URL_PREFIX_HTTP))
                && !url.endsWith("/")) {
            try {
                new URL(url);
                return true;
            } catch (MalformedURLException e) {
                // ignore
            }
        }
        return false;
    }

    @Override
    public void applyTo(TaskRepository repository) {
        
        super.applyTo(repository);
        
        new ReviewboardRepositoryMapper(repository).setCategoryIfNotSet();
    }
    
    @Override
    public boolean canFlipToNextPage() {
        return false;
    }
    
    
    
    void createDefaultSettings(Composite c) {
        Composite control = c;
        
        GridLayout layout = new GridLayout(3, false);
        control.setLayout(layout);
        control.setLayoutData(new GridData(SWT.LEFT, SWT.BEGINNING, false, false, 2, 1));
        
        Label textLabel = new Label(control, SWT.NONE);
        textLabel.setText("Optional default settings: ");
        GridDataFactory.swtDefaults().span(3, SWT.DEFAULT).applyTo(textLabel);
        
        Label targetUserLabel = new Label(control, SWT.NONE);
        targetUserLabel.setText("Target user:");
        
        final Text targetUser = new Text(control, SWT.NONE);
        GridDataFactory.fillDefaults().hint(300, SWT.DEFAULT).grab(true, false).span(2, SWT.DEFAULT).applyTo(targetUser);
        targetUser.setText(ReviewboardUiPlugin.getDefault().getPreferenceStore().getString("targetUser"));
        targetUser.addModifyListener(new ModifyListener() {
            
            public void modifyText(ModifyEvent e) {
                ReviewboardUiPlugin.getDefault().getPreferenceStore().setDefault("targetUser", targetUser.getText());
                    
                getContainer().updateButtons();
                
            }
        });
        
        Label targetGroupLabel = new Label(control, SWT.NONE);
        targetGroupLabel.setText("Target group:");

        final Text targetGroup = new Text(control, SWT.NONE);
        targetGroup.setText(ReviewboardUiPlugin.getDefault().getPreferenceStore().getString("targetGroup"));
        GridDataFactory.fillDefaults().hint(300, SWT.DEFAULT).grab(true, false).span(2, SWT.DEFAULT).applyTo(targetGroup);
        targetGroup.addModifyListener(new ModifyListener() {
            
            public void modifyText(ModifyEvent e) {
                ReviewboardUiPlugin.getDefault().getPreferenceStore().setDefault("targetGroup", targetGroup.getText());
                    
                getContainer().updateButtons();
                
            }
        });
        
        final Button guessSummary = new Button(control, SWT.CHECK);
        guessSummary.setText("Guess Summary");
        guessSummary.setSelection(ReviewboardUiPlugin.getDefault().getPreferenceStore().getBoolean("guessSummary"));
        guessSummary.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                
                ReviewboardUiPlugin.getDefault().getPreferenceStore().setValue("guessSummary", guessSummary.getSelection());
                getContainer().updateButtons();
            }

        });
        
        
        final Button guessDescription = new Button(control, SWT.CHECK);
        guessDescription.setText("Guess Description");
        guessDescription.setSelection(ReviewboardUiPlugin.getDefault().getPreferenceStore().getBoolean("guessDescription"));
        guessDescription.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                ReviewboardUiPlugin.getDefault().getPreferenceStore().setValue("guessDescription", guessDescription.getSelection());
                getContainer().updateButtons();
            }
        });
    }
    
    
}
