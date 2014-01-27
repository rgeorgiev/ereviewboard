package org.review_board.ereviewboard.internal.actions;


import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.egit.core.GitProvider;
import org.eclipse.egit.core.project.GitProjectData;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.team.core.RepositoryProvider;
import org.review_board.ereviewboard.core.ReviewboardDiffMapper;
import org.review_board.ereviewboard.core.model.FileDiff;
import org.review_board.ereviewboard.core.model.Repository;


/**
 * The <tt>ReviewboardToSvnMapper</tt> maps between various Reviewboard items and their SVN correspondents
 * 
 * @author Robert Munteanu
 * 
 */
public class ReviewboardToGitMapper {

    public IProject findProjectForRepository(Repository codeRepository, TaskRepository taskRepository, ReviewboardDiffMapper diffMapper) {

        IWorkspace workspace = ResourcesPlugin.getWorkspace();

        List<IProject> candidates = new ArrayList<IProject>();

        for (IProject project : workspace.getRoot().getProjects()) {

            GitProvider gitProvider = (GitProvider) RepositoryProvider.getProvider(project);

            if (gitProvider == null)
                continue;

            GitProjectData data = gitProvider.getData();

            RepositoryMapping repositoryMapping = data.getRepositoryMapping(project);
            
            //the local git repository for the project
            org.eclipse.jgit.lib.Repository projectGitResource = repositoryMapping.getRepository();
            
            String gitRepositoryName = projectGitResource.getWorkTree().getName();

            if (codeRepository.getName().equals(gitRepositoryName))
                candidates.add(project);
        }

        if (candidates.isEmpty())
            return null;

        if (candidates.size() == 1)
            return candidates.get(0);
        
        // multiple choice - use the latest diff revision to match based on files
        projects: for (IProject project : candidates) {

            Integer latestDiffRevisionId = diffMapper.getLatestDiffRevisionId();

            if (latestDiffRevisionId == null)
                break;
            
            GitProvider gitProvider = (GitProvider) RepositoryProvider.getProvider(project);

            GitProjectData data = gitProvider.getData();

            RepositoryMapping repositoryMapping = data.getRepositoryMapping(project);

            org.eclipse.jgit.lib.Repository projectGitResource = repositoryMapping.getRepository();
            		
            
            
            String projectRelativePath = project.getFullPath().toString();
            
            if (projectRelativePath.startsWith("/")) {
            	projectRelativePath = projectRelativePath.substring(1);
            }
            
            
            if ( !projectRelativePath.endsWith("/") )
                projectRelativePath += "/";

            for (FileDiff fileDiff : diffMapper.getFileDiffs(latestDiffRevisionId.intValue()))
                if (!fileDiff.getDestinationFile().startsWith(projectRelativePath))
                    continue projects;
            
            return project;

        }
        return null;
    }

}
