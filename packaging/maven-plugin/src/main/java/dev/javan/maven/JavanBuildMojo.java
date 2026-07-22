package dev.javan.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Builds the Maven project's compiled classes through the installed Javan binary. */
@Mojo(name = "build", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class JavanBuildMojo extends AbstractMojo {
    /** Maven project whose compiled output is built. */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** Installed binary name or absolute path. */
    @Parameter(property = "javan.executable", defaultValue = "javan")
    private String executable;

    /** Optional explicit entry point when the project contains multiple mains. */
    @Parameter(property = "javan.main")
    private String mainClass;

    /** Invokes {@code javan build} after Maven has compiled the project. */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final ProcessBuilder processBuilder = new ProcessBuilder(command())
            .directory(project.getBasedir())
            .inheritIO();
        try {
            final int exitCode = processBuilder.start().waitFor();
            if (exitCode != 0) {
                throw new MojoFailureException("Javan build failed with exit code " + exitCode);
            }
        } catch (IOException exception) {
            throw new MojoExecutionException(
                "Unable to start Javan executable '" + executable + "'. Set -Djavan.executable=/path/to/javan.",
                exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Interrupted while running Javan build.", exception);
        }
    }

    private List<String> command() throws MojoExecutionException {
        final List<String> command = new ArrayList<>();
        command.addAll(List.of(
            executable, "build", project.getBasedir().getPath(), "--classes", project.getBuild().getOutputDirectory()
        ));
        if (mainClass != null && !mainClass.isBlank()) {
            command.add("--main");
            command.add(mainClass);
        }
        try {
            final List<String> classpath = List.copyOf(project.getCompileClasspathElements());
            if (!classpath.isEmpty()) {
                command.add("--classpath");
                command.add(String.join(java.io.File.pathSeparator, classpath));
            }
        } catch (Exception exception) {
            throw new MojoExecutionException("Unable to resolve Maven compile classpath for Javan.", exception);
        }
        return List.copyOf(command);
    }
}
