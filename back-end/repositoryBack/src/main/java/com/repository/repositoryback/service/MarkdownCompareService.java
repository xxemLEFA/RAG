package com.repository.repositoryback.service;

import com.repository.repositoryback.dto.MarkdownCompareResponse;
import com.repository.repositoryback.dto.DirectoryBrowseResponse;
import com.repository.repositoryback.dto.MarkdownDiffHunk;
import com.repository.repositoryback.dto.MarkdownDiffLine;
import com.repository.repositoryback.dto.MarkdownModifiedFileItem;
import com.repository.repositoryback.dto.MarkdownSyncResponse;
import org.springframework.stereotype.Service;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.InputStream;
import java.awt.GraphicsEnvironment;
import java.nio.file.StandardCopyOption;
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

    public DirectoryBrowseResponse browseDirectory(String initialDir, String dialogTitle) {
        if (!GraphicsEnvironment.isHeadless()) {
            return browseDirectoryWithSwing(initialDir, dialogTitle);
        }

        if (isWindows()) {
            return browseDirectoryWithPowerShell(initialDir, dialogTitle);
        }

        throw new IllegalStateException("当前运行环境不支持弹出目录选择框");
    }

    private DirectoryBrowseResponse browseDirectoryWithSwing(String initialDir, String dialogTitle) {
        final String[] selectedPath = new String[1];
        final RuntimeException[] error = new RuntimeException[1];

        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setDialogTitle(
                            dialogTitle == null || dialogTitle.isBlank() ? "选择目录" : dialogTitle
                    );
                    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    chooser.setMultiSelectionEnabled(false);
                    chooser.setAcceptAllFileFilterUsed(false);

                    Path initialPath = resolveInitialDirectory(initialDir);
                    if (initialPath != null) {
                        chooser.setCurrentDirectory(initialPath.toFile());
                        chooser.setSelectedFile(initialPath.toFile());
                    }

                    int result = chooser.showOpenDialog(null);
                    if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
                        error[0] = new IllegalArgumentException("已取消目录选择");
                        return;
                    }

                    selectedPath[0] = chooser.getSelectedFile().toPath().toAbsolutePath().normalize().toString();
                } catch (RuntimeException exception) {
                    error[0] = exception;
                }
            });
        } catch (Exception exception) {
            if (error[0] != null) {
                throw error[0];
            }
            throw new RuntimeException("打开目录选择框失败", exception);
        }

        if (error[0] != null) {
            throw error[0];
        }
        return new DirectoryBrowseResponse(selectedPath[0]);
    }

    private DirectoryBrowseResponse browseDirectoryWithPowerShell(String initialDir, String dialogTitle) {
        Path initialPath = resolveInitialDirectory(initialDir);
        String escapedTitle = escapePowerShellString(dialogTitle == null || dialogTitle.isBlank() ? "选择目录" : dialogTitle);
        String escapedInitialPath = escapePowerShellString(initialPath == null ? "" : initialPath.toString());

        String script = String.join("\n",
                "Add-Type -AssemblyName System.Windows.Forms",
                "$dialog = New-Object System.Windows.Forms.FolderBrowserDialog",
                "$dialog.Description = '" + escapedTitle + "'",
                "$dialog.ShowNewFolderButton = $true",
                "if ('" + escapedInitialPath + "' -ne '') { $dialog.SelectedPath = '" + escapedInitialPath + "' }",
                "$result = $dialog.ShowDialog()",
                "if ($result -ne [System.Windows.Forms.DialogResult]::OK -or [string]::IsNullOrWhiteSpace($dialog.SelectedPath)) {",
                "  exit 2",
                "}",
                "[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()",
                "Write-Output $dialog.SelectedPath"
        );

        ProcessBuilder processBuilder = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-STA",
                "-Command",
                script
        );

        try {
            Process process = processBuilder.start();
            String stdout = readFully(process.getInputStream()).trim();
            String stderr = readFully(process.getErrorStream()).trim();
            int exitCode = process.waitFor();

            if (exitCode == 0 && !stdout.isBlank()) {
                return new DirectoryBrowseResponse(Path.of(stdout).toAbsolutePath().normalize().toString());
            }
            if (exitCode == 2) {
                throw new IllegalArgumentException("已取消目录选择");
            }

            String detail = stderr.isBlank() ? stdout : stderr;
            throw new RuntimeException(detail.isBlank() ? "打开目录选择框失败" : detail);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("打开目录选择框被中断", exception);
        } catch (IOException exception) {
            throw new RuntimeException("打开目录选择框失败", exception);
        }
    }

    public MarkdownSyncResponse syncFiles(String sourceDir, String targetDir, String destination, List<String> relativePaths) {
        Path sourcePath = validateDirectory(sourceDir, "源目录");
        Path targetPath = validateDirectory(targetDir, "对比目录");

        SyncPlan syncPlan = resolveSyncPlan(sourcePath, targetPath, destination);
        List<String> syncedFiles = new ArrayList<>();

        for (String relativePath : relativePaths) {
            Path normalizedRelativePath = normalizeRelativePath(relativePath);
            Path sourceFile = syncPlan.copyFrom().resolve(normalizedRelativePath).normalize();
            Path destinationFile = syncPlan.copyTo().resolve(normalizedRelativePath).normalize();

            ensureWithinRoot(sourceFile, syncPlan.copyFrom(), "同步来源");
            ensureWithinRoot(destinationFile, syncPlan.copyTo(), "同步目标");

            if (!Files.exists(sourceFile) || !Files.isRegularFile(sourceFile)) {
                throw new IllegalArgumentException("同步来源缺少文件: " + relativePath);
            }

            try {
                Path parent = destinationFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(sourceFile, destinationFile, StandardCopyOption.REPLACE_EXISTING);
                syncedFiles.add(toRelativePath(syncPlan.copyTo(), destinationFile));
            } catch (IOException exception) {
                throw new RuntimeException("同步文件失败: " + relativePath, exception);
            }
        }

        return new MarkdownSyncResponse(
                sourcePath.toString(),
                targetPath.toString(),
                syncPlan.copyFrom().toString(),
                syncPlan.copyTo().toString(),
                syncedFiles
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

    private SyncPlan resolveSyncPlan(Path sourcePath, Path targetPath, String destination) {
        if ("source".equalsIgnoreCase(destination)) {
            return new SyncPlan(targetPath, sourcePath);
        }
        if ("target".equalsIgnoreCase(destination)) {
            return new SyncPlan(sourcePath, targetPath);
        }
        throw new IllegalArgumentException("同步目标只能是 source 或 target");
    }

    private Path resolveInitialDirectory(String initialDir) {
        if (initialDir == null || initialDir.isBlank()) {
            return null;
        }

        Path path = Path.of(initialDir).toAbsolutePath().normalize();
        if (Files.isDirectory(path)) {
            return path;
        }

        Path parent = path.getParent();
        if (parent != null && Files.isDirectory(parent)) {
            return parent;
        }
        return null;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private String escapePowerShellString(String value) {
        return value.replace("'", "''");
    }

    private String readFully(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
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

    private Path normalizeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        Path normalized = Path.of(relativePath).normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..")) {
            throw new IllegalArgumentException("文件路径不合法: " + relativePath);
        }
        String fileName = normalized.getFileName() == null ? "" : normalized.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".md")) {
            throw new IllegalArgumentException("只允许同步 Markdown 文件: " + relativePath);
        }
        return normalized;
    }

    private void ensureWithinRoot(Path path, Path root, String label) {
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException(label + "路径越界: " + path);
        }
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

    private record SyncPlan(Path copyFrom, Path copyTo) {
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
