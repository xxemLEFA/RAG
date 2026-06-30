package com.repository.repositoryback.service;

import com.repository.repositoryback.dto.MarkdownCompareResponse;
import com.repository.repositoryback.dto.MarkdownModifiedFileItem;
import com.repository.repositoryback.dto.MarkdownSyncResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownCompareServiceTest {

    private final MarkdownCompareService service = new MarkdownCompareService();

    @TempDir
    Path tempDir;

    @Test
    void comparesMarkdownDirectoriesRecursively() throws IOException {
        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path targetDir = Files.createDirectory(tempDir.resolve("target"));

        write(sourceDir.resolve("same.md"), "# title\nsame\n");
        write(targetDir.resolve("same.md"), "# title\nsame\n");

        write(sourceDir.resolve("removed.md"), "old only\n");
        write(targetDir.resolve("added.md"), "new only\n");

        Files.createDirectories(sourceDir.resolve("nested"));
        Files.createDirectories(targetDir.resolve("nested"));
        write(sourceDir.resolve("nested/doc.md"), "line 1\nline 2\nline 4\n");
        write(targetDir.resolve("nested/doc.md"), "line 1\nline 3\nline 4\n");

        MarkdownCompareResponse response = service.compareDirectories(sourceDir.toString(), targetDir.toString());

        assertEquals(3, response.sourceFileCount());
        assertEquals(3, response.targetFileCount());
        assertEquals(1, response.unchangedCount());
        assertIterableEquals(java.util.List.of("added.md"), response.addedFiles());
        assertIterableEquals(java.util.List.of("removed.md"), response.removedFiles());
        assertEquals(1, response.modifiedFiles().size());

        MarkdownModifiedFileItem modified = response.modifiedFiles().get(0);
        assertEquals("nested/doc.md", modified.relativePath());
        assertEquals(1, modified.additions());
        assertEquals(1, modified.deletions());
        assertFalse(modified.hunks().isEmpty());
        assertTrue(modified.hunks().get(0).lines().stream().anyMatch(line -> "remove".equals(line.type())));
        assertTrue(modified.hunks().get(0).lines().stream().anyMatch(line -> "add".equals(line.type())));
    }

    @Test
    void syncsSelectedMarkdownFilesIntoTargetDirectory() throws IOException {
        Path sourceDir = Files.createDirectory(tempDir.resolve("sync-source"));
        Path targetDir = Files.createDirectory(tempDir.resolve("sync-target"));

        Files.createDirectories(sourceDir.resolve("nested"));
        write(sourceDir.resolve("added.md"), "added from source\n");
        write(sourceDir.resolve("nested/doc.md"), "latest content\n");
        write(targetDir.resolve("nested/doc.md"), "old content\n");

        MarkdownSyncResponse response = service.syncFiles(
                sourceDir.toString(),
                targetDir.toString(),
                "target",
                java.util.List.of("added.md", "nested/doc.md")
        );

        assertIterableEquals(java.util.List.of("added.md", "nested/doc.md"), response.syncedFiles());
        assertEquals("added from source\n", Files.readString(targetDir.resolve("added.md")));
        assertEquals("latest content\n", Files.readString(targetDir.resolve("nested/doc.md")));
    }

    private void write(Path path, String content) throws IOException {
        Files.writeString(path, content);
    }
}
