package com.vackosar.gitflowincrementalbuild.boundary;

import com.google.inject.Guice;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component(role = AbstractMavenLifecycleParticipant.class)
public class MavenLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    public static final String GIB = "gib.enable";
    public static final String TRUE = "true";

    @Requirement private Logger logger;

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        mergeGibProperties(session);
        if (! TRUE.equalsIgnoreCase(System.getProperty(GIB))) {
            logger.info("Skipping GIB because property '" + GIB + "' not set to '" + TRUE + "'.");
            return;
        }
        try {
            Set<String> moduleNames = Guice
                    .createInjector(new Module(new String[] {session.getTopLevelProject().getFile().getPath()}))
                    .getInstance(Executor.class)
                    .getArtifactIds();
            logger.info("moduleNames:");
            moduleNames.stream().forEach(logger::info);
            Set<MavenProject> changedProjects = new HashSet<>();
            for (MavenProject mavenProject: session.getProjects()) {
                if (moduleNames.contains(mavenProject.getArtifactId())) {
                    changedProjects.addAll(getAllDependents(session.getProjects(), mavenProject));
                }
            }
            logger.info("changedProjects:");
            changedProjects.stream().map(MavenProject::toString).forEach(logger::info);
            session.getProjects().retainAll(changedProjects);
        } catch (Exception e) {
            throw new MavenExecutionException("Exception", e);
        }
    }

    private void mergeGibProperties(MavenSession mavenSession) {
        mavenSession.getTopLevelProject().getProperties().entrySet().stream()
                .filter(e->e.getKey().toString().startsWith("gib."))
                .filter(e->System.getProperty(e.getKey().toString()) == null)
                .forEach(e->System.setProperty(e.getKey().toString(), e.getValue().toString()));
    }

    private Set<MavenProject> getAllDependents(List<MavenProject> projects, MavenProject project) {
        Set<MavenProject> result = new HashSet<>();
        result.add(project);
        for (MavenProject possibleDependent: projects) {
            for (Dependency dependency : possibleDependent.getDependencies()) {
                if (equals(project, dependency)) {
                    result.addAll(getAllDependents(projects, possibleDependent));
                }
            }
            if (project.equals(possibleDependent.getParent())) {
                result.addAll(getAllDependents(projects, possibleDependent));
            }
        }
        return result;
    }

    private boolean equals(MavenProject project, Dependency dependency) {
        return dependency.getArtifactId().equals(project.getArtifactId())
        && dependency.getGroupId().equals(project.getGroupId())
        && dependency.getVersion().equals(project.getVersion());
    }

}
