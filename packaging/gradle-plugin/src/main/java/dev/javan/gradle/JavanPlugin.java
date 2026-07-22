package dev.javan.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Thin Gradle adapter that delegates analysis and native builds to the installed Javan binary. */
public final class JavanPlugin implements Plugin<Project> {
    /** Applies the public {@code javanCheck} and {@code javanBuild} tasks to a Java project. */
    @Override
    public void apply(final Project project) {
        project.getPluginManager().withPlugin("java", ignored -> registerTasks(project));
    }

    private static void registerTasks(final Project project) {
        final SourceSet main = project.getExtensions()
            .getByType(SourceSetContainer.class)
            .getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        project.getTasks().register("javanCheck", Exec.class, task -> {
            task.dependsOn(main.getClassesTaskName());
            task.setWorkingDir(project.getProjectDir());
            task.commandLine(command(project, main, "check"));
        });
        project.getTasks().register("javanBuild", Exec.class, task -> {
            task.dependsOn(main.getClassesTaskName());
            task.setWorkingDir(project.getProjectDir());
            task.commandLine(command(project, main, "build"));
        });
        project.getTasks().register("javanRun", Exec.class, task -> {
            task.dependsOn(main.getClassesTaskName());
            task.setWorkingDir(project.getProjectDir());
            task.commandLine(command(project, main, "run"));
        });
    }

    private static List<String> command(final Project project, final SourceSet main, final String operation) {
        final List<String> command = new ArrayList<>();
        command.add(String.valueOf(project.findProperty("javan.executable") == null
            ? "javan" : project.findProperty("javan.executable")));
        command.add(operation);
        command.add(project.getProjectDir().getPath());
        for (final File classes : main.getOutput().getClassesDirs().getFiles()) {
            command.add("--classes");
            command.add(classes.getPath());
        }
        final FileCollection runtimeClasspath = main.getRuntimeClasspath();
        if (!runtimeClasspath.isEmpty()) {
            command.add("--classpath");
            command.add(String.join(File.pathSeparator, runtimeClasspath.getAsPath().split(File.pathSeparator)));
        }
        final Object mainClass = project.findProperty("javan.main");
        if (mainClass != null && !String.valueOf(mainClass).isBlank()) {
            command.add("--main");
            command.add(String.valueOf(mainClass));
        }
        return List.copyOf(command);
    }
}
