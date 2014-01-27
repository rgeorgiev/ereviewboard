package org.review_board.ereviewboard.ui.preferences;

import org.eclipse.jface.preference.*;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.IWorkbench;
import org.review_board.ereviewboard.ui.ReviewboardUiPlugin;

/**
 * This class represents a preference page that
 * is contributed to the Preferences dialog. By 
 * subclassing <samp>FieldEditorPreferencePage</samp>, we
 * can use the field support built into JFace that allows
 * us to create a page that is small and knows how to 
 * save, restore and apply itself.
 * <p>
 * This page is used to modify preferences only. They
 * are stored in the preference store that belongs to
 * the main plug-in class. That way, preferences can
 * be accessed directly via the preference store.
 */

public class ReviewRequestPage
	extends FieldEditorPreferencePage
	implements IWorkbenchPreferencePage {

	public ReviewRequestPage() {
		super(GRID);
		setPreferenceStore(ReviewboardUiPlugin.getDefault().getPreferenceStore());
		setDescription("A demonstration of a preference page implementation");
	}
	
	/**
	 * Creates the field editors. Field editors are abstractions of
	 * the common GUI blocks needed to manipulate various types
	 * of preferences. Each field editor knows how to save and
	 * restore itself.
	 */
	public void createFieldEditors() {
	    addField(new StringFieldEditor("targetUser", "Default target user",getFieldEditorParent()));
        addField(new StringFieldEditor("targetGroup", "Default target group",getFieldEditorParent()));
        addField(new BooleanFieldEditor("guessSummary","Guess summary",getFieldEditorParent()));
        addField(new BooleanFieldEditor("guessDescription","Guess description",getFieldEditorParent()));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
	 */
	public void init(IWorkbench workbench) {
	}
	
}