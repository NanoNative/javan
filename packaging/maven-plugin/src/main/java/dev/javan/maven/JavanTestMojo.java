package dev.javan.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.util.List;

/** Delegates the Maven project test lifecycle to Javan's public test command. */
@Mojo(name = "test", defaultPhase = LifecyclePhase.TEST, threadSafe = true)
public final class JavanTestMojo extends AbstractMojo {
    /** Maven project whose tests are run. */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** Installed binary name or absolute path. */
    @Parameter(property = "javan.executable", defaultValue = "javan")
    private String executable;

    /** Delegates to {@code javan test} after Maven has produced classes. */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final ProcessBuilder processBuilder = new ProcessBuilder(
            List.of(executable, "test", project.getBasedir().getPath())
        ).directory(project.getBasedir()).inheritIO();
        try {
            final int exitCode = processBuilder.start().waitFor();
            if (exitCode != 0) {
                throw new MojoFailureException("Javan test failed with exit code " + exitCode);
            }
        } catch (IOException exception) {
            throw new MojoExecutionException(
                "Unable to start Javan executable '" + executable + "'. Set -Djavan.executable=/path/to/javan.",
                exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Interrupted while running Javan tests.", exception);
        }
    }
}
