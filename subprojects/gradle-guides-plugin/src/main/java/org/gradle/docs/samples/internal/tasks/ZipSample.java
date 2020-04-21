package org.gradle.docs.samples.internal.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.apache.tools.zip.UnixStat;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipOutputStream;

import java.io.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Zips a sample to the given location.
 *
 * Skips execution if there are no "main" content.  This is usually DSL-specific content.
 */
public abstract class ZipSample extends DefaultTask {
    @InputFiles
    protected FileTree getSourceAsTree() {
        return getSource().getAsFileTree();
    }

    @InputFiles
    @SkipWhenEmpty
    protected FileTree getMainSourceAsTree() {
        return getMainSource().getAsFileTree();
    }

    @Internal
    public abstract ConfigurableFileCollection getSource();

    @Internal
    public abstract ConfigurableFileCollection getMainSource();

    @Internal
    public abstract ListProperty<String> getExcludes();

    @Input
    public abstract Property<String> getReadmeName();

    @OutputFile
    public abstract RegularFileProperty getArchiveFile();

    @TaskAction
    private void zip() {
        File zipFile = getArchiveFile().get().getAsFile();
        zipFile.delete();

        try (FileOutputStream fileStream = new FileOutputStream(zipFile);
             ZipOutputStream zipStream = new ZipOutputStream(fileStream)) {
            zipStream.setMethod(ZipOutputStream.DEFLATED);
            Set<String> dirs = new HashSet<>();
            getFilteredSourceTree().visit(new FileVisitor() {
                @Override
                public void visitDir(FileVisitDetails dirDetails) {
                    try {
                        String dirPath = dirDetails.getRelativePath().getPathString();
                        if (!dirs.add(dirPath)) {
                            return;
                        }
                        ZipEntry entry = new ZipEntry(dirDetails.getRelativePath().getPathString() + "/");
                        entry.setUnixMode(UnixStat.DIR_FLAG | dirDetails.getMode());
                        zipStream.putNextEntry(entry);
                        zipStream.closeEntry();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }

                @Override
                public void visitFile(FileVisitDetails fileDetails) {
                    try {
                        final ZipEntry entry;
                        if (fileDetails.getName().equals(getReadmeName().get())) {
                            entry = new ZipEntry("README");
                        } else {
                            entry = new ZipEntry(fileDetails.getRelativePath().getPathString());
                        }

                        entry.setSize(fileDetails.getSize());
                        entry.setUnixMode(UnixStat.FILE_FLAG | fileDetails.getMode());
                        zipStream.putNextEntry(entry);

                        if (Arrays.asList("build.gradle", "settings.gradle", "build.gradle.kts", "settings.gradle.kts").contains(fileDetails.getName())) {
                            // TODO (donat) this is a hacky workaround. The proper solution could be to introduce a preprocess phase for all samples
                            zipStream.write(filterUserGuideRefs(fileDetails).getBytes());
                        } else {
                            fileDetails.copyTo(zipStream);
                        }

                        zipStream.closeEntry();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String filterUserGuideRefs(FileVisitDetails fileDetails) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        fileDetails.copyTo(bos);

        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new StringReader(bos.toString()));
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.contains("// tag::") && !line.contains("// end::")) {
                builder.append(line);
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private FileTree getFilteredSourceTree() {
        return getSourceAsTree().matching(f -> f.exclude(getExcludes().get()));
    }
}
