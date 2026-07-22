package dev.javan.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs the installed Javan checker after Maven has produced project classes. */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public final class JavanCheckMojo extends AbstractMojo {
    /** Maven project whose compiled output is inspected. */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** Installed binary name or absolute path. */
    @Parameter(property = "javan.executable", defaultValue = "javan")
    private String executable;

    /** Optional explicit entry point when the project contains multiple mains. */
    @Parameter(property = "javan.main")
    private String mainClass;

    /** Invokes {@code javan check} using Maven's output and resolved compile classpath. */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final List<String> command = command();
        final ProcessBuilder processBuilder = new ProcessBuilder(command)
            .directory(project.getBasedir())
            .inheritIO();
        try {
            final int exitCode = processBuilder.start().waitFor();
            if (exitCode != 0) {
                throw new MojoFailureException("Javan check failed with exit code " + exitCode);
            }
        } catch (IOException exception) {
            throw new MojoExecutionException(
                "Unable to start Javan executable '" + executable + "'. Set -Djavan.executable=/path/to/javan.",
                exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Interrupted while running Javan check.", exception);
        }
    }

    private List<String> command() throws MojoExecutionException {
        final String classes = project.getBuild().getOutputDirectory();
        final List<String> command = new ArrayList<>();
        command.addAll(List.of(executable, "check", project.getBasedir().getPath(), "--classes", classes));
        if (mainClass != null && !mainClass.isBlank()) {
            command.add("--main");
            command.add(mainClass);
        }
        final List<String> classpath = compileClasspath();
        if (!classpath.isEmpty()) {
            command.add("--classpath");
            command.add(String.join(java.io.File.pathSeparator, classpath));
        }
        return List.copyOf(command);
    }

    private List<String> compileClasspath() throws MojoExecutionException {
        try {
            return List.copyOf(project.getCompileClasspathElements());
        } catch (Exception exception) {
            throw new MojoExecutionException("Unable to resolve Maven compile classpath for Javan.", exception);
        }
    }
}
