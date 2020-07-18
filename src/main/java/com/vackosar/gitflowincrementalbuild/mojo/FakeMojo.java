/**
 *
 */
package com.vackosar.gitflowincrementalbuild.mojo;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * This "fake" mojo is only a "vessel" that is enriched by {@link MojoParametersGeneratingByteBuddyPlugin} with members based on the enum instances in
 * {@link com.vackosar.gitflowincrementalbuild.control.Property Property}.
 *
 * @see MojoParametersGeneratingByteBuddyPlugin
 */
public class FakeMojo extends AbstractMojo {

    // bytebuddy will add members

    @Override
    public void execute() throws MojoExecutionException {
        throw new MojoExecutionException("This fake goal/mojo shall not be executed!");
    }
}
