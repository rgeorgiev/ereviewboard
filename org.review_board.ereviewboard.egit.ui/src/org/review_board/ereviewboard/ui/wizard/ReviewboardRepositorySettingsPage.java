package org.review_board.ereviewboard.ui.wizard;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.ui.TasksUi;
import org.eclipse.mylyn.tasks.ui.wizards.AbstractRepositorySettingsPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.review_board.ereviewboard.core.ReviewboardCorePlugin;
import org.review_board.ereviewboard.core.ReviewboardRepositoryConnector;
import org.review_board.ereviewboard.core.ReviewboardRepositoryMapper;
import org.review_board.ereviewboard.core.client.ReviewboardClient;

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
        
        Composite control = getControl().getParent();
        
        GridLayoutFactory.swtDefaults().applyTo(control);
        
        Label descriptionLabel = new Label(control, SWT.NONE );
        descriptionLabel.setText("Test");
        setControl(control);
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
    public boolean canFlipToNextPage() {
        return false;
    }

    @Override
    public void applyTo(TaskRepository repository) {
        
        super.applyTo(repository);
        
        new ReviewboardRepositoryMapper(repository).setCategoryIfNotSet();
    }
}
