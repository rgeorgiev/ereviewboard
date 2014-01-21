package org.review_board.ereviewboard.ui.wizard;


import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.review_board.ereviewboard.ui.util.UiUtils;


/**
 * @author Robert Munteanu
 *
 */
class UpdateReviewRequestPage extends WizardPage {

    private StyledText _text;

    public UpdateReviewRequestPage() {

        super("Update Review Request", "Update Review Request", null);
        setMessage("Provide an optional description for the changes you are about to publish.");
    }
    
    public void createControl(Composite parent) {
        
        Composite control = new Composite(parent, SWT.NONE);

        GridLayoutFactory.fillDefaults().numColumns(2).applyTo(control);
        
        Label label = new Label( control, SWT.WRAP);
        GridDataFactory.swtDefaults().align(SWT.BEGINNING, SWT.BEGINNING).applyTo(label);
        label.setText("Change description\n(optional)");
        
        _text = UiUtils.newMultilineText(control);

        setControl(control);
    }
    
    public String getChangeDescription() {
        
        return _text.getText();
    }
}
