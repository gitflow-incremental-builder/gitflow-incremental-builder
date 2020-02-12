package com.vackosar.gitflowincrementalbuild.mocks.server;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;

class SinglePredefinedRepoResolver<C> implements RepositoryResolver<C> {

    private final Repository predefinedRepo;

    public SinglePredefinedRepoResolver(Repository predefinedRepo) {
        this.predefinedRepo = predefinedRepo;
    }

    @Override
    public Repository open(C req, String name) {
        predefinedRepo.incrementOpen();
        return predefinedRepo;
    }
}
