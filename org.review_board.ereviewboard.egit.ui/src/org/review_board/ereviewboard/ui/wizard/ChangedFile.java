package org.review_board.ereviewboard.ui.wizard;


import org.eclipse.jgit.diff.DiffEntry;


/**
 * The <tt>ChangedFile</tt> represents a file which has local changes
 * 
 * @author Robert Munteanu
 */
class ChangedFile {

    private final DiffEntry diffEntry;

    private final DiffEntry.ChangeType statusKind;
    
    private final String relativePath;

    private final String copiedFromRelativePath;

    public ChangedFile(DiffEntry diffEntry, DiffEntry.ChangeType statusKind, String relativePath) {

        this(diffEntry, statusKind, relativePath, null);
    }

    public ChangedFile(DiffEntry diffEntry, DiffEntry.ChangeType statusKind, String relativePath, String copiedFromRelativePath) {

        this.diffEntry = diffEntry;
        this.statusKind = statusKind;
        this.relativePath = relativePath;
        this.copiedFromRelativePath = copiedFromRelativePath;
    }

    public DiffEntry getDiffEntry() {

        return diffEntry;
    }

    public DiffEntry.ChangeType getStatusKind() {

        return statusKind;
    }
    
    /**
     * Returns the path of the changed file relative to the parent project's location in the SVN repository
     * 
     * <p>For instance, if the project is located at <tt>http://svn.example.com/project</tt> and the 
     * resource at <tt>http://svn.example.com/project/dir/file.txt</tt> , the relativePath is 
     * <tt>dir/file.txt</tt>. Note that there are not leading slashes</p>
     * 
     * @return the path of the changed file relative to the parent project's location in the SVN repository
     */
    public String getPathRelativeToProject() {
     
        return relativePath;
    }

    /**
     * 
     * @return the relative path of the file this file was copied from, or <code>null</code>
     * @see #getPathRelativeToProject()
     */
    public String getCopiedFromPathRelativeToProject() {

        return copiedFromRelativePath;
    }

    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((diffEntry == null) ? 0 : diffEntry.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof ChangedFile))
            return false;
        ChangedFile other = (ChangedFile) obj;
        if (diffEntry == null) {
            if (other.diffEntry != null)
                return false;
        } else if (!diffEntry.equals(other.diffEntry))
            return false;
        return true;
    }
}
