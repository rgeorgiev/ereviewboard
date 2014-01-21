/*******************************************************************************
 * Copyright (c) 2004, 2011 Robert Munteanu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Robert Munteanu - initial API and implementation
 *******************************************************************************/
package org.review_board.ereviewboard.core.model;

import java.util.Comparator;

/**
 * @author Robert Munteanu
 */
public class DiffComment extends Comment {

    public static Comparator<DiffComment> COMPARATOR_ID = new Comparator<DiffComment>() {

        public int compare(DiffComment o1, DiffComment o2) {
            
            return Integer.valueOf(o1.getId()).compareTo(o2.getId());
        }
    };
    
    private int firstLine;
    private int numLines;
    private int fileId;
    private Boolean _public;

    public int getFirstLine() {
        return firstLine;
    }

    public void setFirstLine(int firstLine) {
        this.firstLine = firstLine;
    }

    public int getNumLines() {
        return numLines;
    }

    public void setNumLines(int numLines) {
        this.numLines = numLines;
    }

    public void setFileId(int fileId) {
        this.fileId = fileId;
    }
    
    public int getFileId() {
        return fileId;
    }

    
    /**
     * Returns true if the comment is public
     * 
     * <p>The field is not present in the RB API and is calculated when loading diff comments attached
     * to reviews and review replies and may not always be present.</p>.
     * 
     * @return true if the comment is public , false is it is a draft, null if not known.
     */
    public Boolean getPublic() {
    
        return _public;
    }

    
    public void setPublic(Boolean _public) {
    
        this._public = _public;
    }

    
}
