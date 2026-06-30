package com.repository.repositoryback.service;

import com.repository.repositoryback.dto.MarkdownCompareResponse;
import com.repository.repositoryback.dto.MarkdownDiffHunk;
import com.repository.repositoryback.dto.MarkdownDiffLine;
import com.repository.repositoryback.dto.MarkdownModifiedFileItem;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

@Service
public class MarkdownCompareService {

    private static final int HUNK_CONTEXT_LINES = 2;

    public MarkdownCompareResponse compareDirectories(String sourceDir, String targetDir) {
        Path sourcePath = validateDirectory(sourceDir, "源目录");
        Path targetPath = validateDirectory(targetDir, "对比目录");

        Map<String, Path> sourceFiles = listMarkdownFiles(sourcePath);
        Map<String, Path> targetFiles = listMarkdownFiles(targetPath);

        List<String> addedFiles = new ArrayList<>();
        List<String> removedFiles = new ArrayList<>();
        List<MarkdownModifiedFileItem> modifiedFiles = new ArrayList<>();
        int unchangedCount = 0;

        TreeSet<String> allPaths = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        allPaths.addAll(sourceFiles.keySet());
        allPaths.addAll(targetFiles.keySet());

        for (String relativePath : allPaths) {
            Path sourceFile = sourceFiles.get(relativePath);
            Path targetFile = targetFiles.get(relativePath);

            if (sourceFile == null) {
                addedFiles.add(relativePath);
                continue;
            }
            if (targetFile == null) {
                removedFiles.add(relativePath);
                continue;
            }

            FileLines sourceLines = readLines(sourceFile, relativePath, "源目录");
            FileLines targetLines = readLines(targetFile, relativePath, "对比目录");
            if (sourceLines.normalizedContent().equals(targetLines.normalizedContent())) {
                unchangedCount++;
                continue;
            }

            modifiedFiles.add(buildModifiedFile(relativePath, sourceLines.lines(), targetLines.lines()));
        }

        modifiedFiles.sort(Comparator.comparing(MarkdownModifiedFileItem::relativePath, String.CASE_INSENSITIVE_ORDER));

        return new MarkdownCompareResponse(
                sourcePath.toString(),
                targetPath.toString(),
                sourceFiles.size(),
                targetFiles.size(),
                unchangedCount,
                addedFiles,
                removedFiles,
                modifiedFiles
        );
    }

    private Path validateDirectory(String directory, String label) {
        if (directory == null || directory.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }

        Path path = Path.of(directory).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException(label + "不存在: " + path);
        }
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException(label + "不是目录: " + path);
        }
        return path;
    }

    private Map<String, Path> listMarkdownFiles(Path directory) {
        try (Stream<Path> stream = Files.walk(directory)) {
            Map<String, Path> files = new LinkedHashMap<>();
            stream.filter(Files::isRegularFile)
                    .filter(this::isMarkdownFile)
                    .sorted(Comparator.comparing(path -> directory.relativize(path).toString(), String.CASE_INSENSITIVE_ORDER))
                    .forEach(path -> files.put(toRelativePath(directory, path), path));
            return files;
        } catch (IOException exception) {
            throw new RuntimeException("读取目录失败: " + directory, exception);
        }
    }

    private boolean isMarkdownFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".md");
    }

    private String toRelativePath(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private FileLines readLines(Path file, String relativePath, String label) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
            List<String> lines = splitLines(content);
            return new FileLines(content, lines);
        } catch (IOException exception) {
            throw new RuntimeException("读取" + label + "文件失败: " + relativePath, exception);
        }
    }

    private List<String> splitLines(String content) {
        if (content.isEmpty()) {
            return List.of();
        }
        return List.of(content.split("\n", -1));
    }

    private MarkdownModifiedFileItem buildModifiedFile(String relativePath, List<String> sourceLines, List<String> targetLines) {
        List<DiffEntry> diffEntries = buildDiffEntries(sourceLines, targetLines);
        List<MarkdownDiffHunk> hunks = buildHunks(diffEntries);

        int additions = 0;
        int deletions = 0;
        for (DiffEntry entry : diffEntries) {
            if (entry.type() == DiffType.ADD) {
                additions++;
            } else if (entry.type() == DiffType.REMOVE) {
                deletions++;
            }
        }

        return new MarkdownModifiedFileItem(
                relativePath,
                sourceLines.size(),
                targetLines.size(),
                additions,
                deletions,
                hunks
        );
    }

    private List<DiffEntry> buildDiffEntries(List<String> sourceLines, List<String> targetLines) {
        int leftSize = sourceLines.size();
        int rightSize = targetLines.size();
        int[][] lcs = new int[leftSize + 1][rightSize + 1];

        for (int left = leftSize - 1; left >= 0; left--) {
            for (int right = rightSize - 1; right >= 0; right--) {
                if (sourceLines.get(left).equals(targetLines.get(right))) {
                    lcs[left][right] = lcs[left + 1][right + 1] + 1;
                } else {
                    lcs[left][right] = Math.max(lcs[left + 1][right], lcs[left][right + 1]);
                }
            }
        }

        List<DiffEntry> entries = new ArrayList<>();
        int left = 0;
        int right = 0;
        int sourceLineNumber = 1;
        int targetLineNumber = 1;

        while (left < leftSize && right < rightSize) {
            String sourceLine = sourceLines.get(left);
            String targetLine = targetLines.get(right);

            if (sourceLine.equals(targetLine)) {
                entries.add(new DiffEntry(DiffType.CONTEXT, sourceLine, sourceLineNumber, targetLineNumber));
                left++;
                right++;
                sourceLineNumber++;
                targetLineNumber++;
                continue;
            }

            if (lcs[left + 1][right] >= lcs[left][right + 1]) {
                entries.add(new DiffEntry(DiffType.REMOVE, sourceLine, sourceLineNumber, null));
                left++;
                sourceLineNumber++;
            } else {
                entries.add(new DiffEntry(DiffType.ADD, targetLine, null, targetLineNumber));
                right++;
                targetLineNumber++;
            }
        }

        while (left < leftSize) {
            entries.add(new DiffEntry(DiffType.REMOVE, sourceLines.get(left), sourceLineNumber, null));
            left++;
            sourceLineNumber++;
        }

        while (right < rightSize) {
            entries.add(new DiffEntry(DiffType.ADD, targetLines.get(right), null, targetLineNumber));
            right++;
            targetLineNumber++;
        }

        return entries;
    }

    private List<MarkdownDiffHunk> buildHunks(List<DiffEntry> entries) {
        List<MarkdownDiffHunk> hunks = new ArrayList<>();
        int index = 0;
        while (index < entries.size()) {
            while (index < entries.size() && entries.get(index).type() == DiffType.CONTEXT) {
                index++;
            }
            if (index >= entries.size()) {
                break;
            }

            int firstChange = index;
            int lastChange = index;
            int scan = index;
            while (scan < entries.size()) {
                if (entries.get(scan).type() != DiffType.CONTEXT) {
                    lastChange = scan;
                } else if (scan - lastChange > HUNK_CONTEXT_LINES) {
                    break;
                }
                scan++;
            }

            int from = Math.max(0, firstChange - HUNK_CONTEXT_LINES);
            int to = Math.min(entries.size(), lastChange + HUNK_CONTEXT_LINES + 1);
            List<MarkdownDiffLine> lines = new ArrayList<>();
            for (int i = from; i < to; i++) {
                DiffEntry entry = entries.get(i);
                lines.add(new MarkdownDiffLine(
                        entry.type().jsonValue,
                        entry.sourceLineNumber(),
                        entry.targetLineNumber(),
                        entry.content()
                ));
            }

            hunks.add(new MarkdownDiffHunk(
                    findStartLine(lines, true),
                    findStartLine(lines, false),
                    lines
            ));
            index = to;
        }
        return hunks;
    }

    private int findStartLine(List<MarkdownDiffLine> lines, boolean source) {
        for (MarkdownDiffLine line : lines) {
            Integer number = source ? line.sourceLineNumber() : line.targetLineNumber();
            if (number != null) {
                return number;
            }
        }
        return 1;
    }

    private record FileLines(String normalizedContent, List<String> lines) {
    }

    private record DiffEntry(DiffType type, String content, Integer sourceLineNumber, Integer targetLineNumber) {
    }

    private enum DiffType {
        CONTEXT("context"),
        ADD("add"),
        REMOVE("remove");

        private final String jsonValue;

        DiffType(String jsonValue) {
            this.jsonValue = jsonValue;
        }
    }
}
