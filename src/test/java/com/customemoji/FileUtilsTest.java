package com.customemoji;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.customemoji.features.loader.FileUtils;

public class FileUtilsTest
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void isEmojiFile_pngExtension_returnsTrue()
    {
        assertTrue(FileUtils.isEmojiFile(Path.of("test.png")));
    }

    @Test
    public void isEmojiFile_pngUppercase_returnsTrue()
    {
        assertTrue(FileUtils.isEmojiFile(Path.of("test.PNG")));
    }

    @Test
    public void isEmojiFile_jpgExtension_returnsTrue()
    {
        assertTrue(FileUtils.isEmojiFile(Path.of("test.jpg")));
    }

    @Test
    public void isEmojiFile_jpegExtension_returnsTrue()
    {
        assertTrue(FileUtils.isEmojiFile(Path.of("test.jpeg")));
    }

    @Test
    public void isEmojiFile_gifExtension_returnsTrue()
    {
        assertTrue(FileUtils.isEmojiFile(Path.of("test.gif")));
    }

    @Test
    public void isEmojiFile_wavExtension_returnsFalse()
    {
        assertFalse(FileUtils.isEmojiFile(Path.of("test.wav")));
    }

    @Test
    public void isEmojiFile_txtExtension_returnsFalse()
    {
        assertFalse(FileUtils.isEmojiFile(Path.of("test.txt")));
    }

    @Test
    public void isEmojiFile_noExtension_returnsFalse()
    {
        assertFalse(FileUtils.isEmojiFile(Path.of("testfile")));
    }

    @Test
    public void isSoundojiFile_wavExtension_returnsTrue()
    {
        assertTrue(FileUtils.isSoundojiFile(Path.of("sound.wav")));
    }

    @Test
    public void isSoundojiFile_wavUppercase_returnsTrue()
    {
        assertTrue(FileUtils.isSoundojiFile(Path.of("sound.WAV")));
    }

    @Test
    public void isSoundojiFile_pngExtension_returnsFalse()
    {
        assertFalse(FileUtils.isSoundojiFile(Path.of("image.png")));
    }

    @Test
    public void isSoundojiFile_mp3Extension_returnsFalse()
    {
        assertFalse(FileUtils.isSoundojiFile(Path.of("sound.mp3")));
    }

    @Test
    public void extractFileNameFromErrorMessage_withColorTag_extractsFileName()
    {
        String errorMessage = "Error loading <col=ff0000>C:\\Users\\test\\emoji.png</col>";
        String result = FileUtils.extractFileNameFromErrorMessage(errorMessage);
        assertEquals("emoji.png", result);
    }

    @Test
    public void extractFileNameFromErrorMessage_nullMessage_returnsEmpty()
    {
        String result = FileUtils.extractFileNameFromErrorMessage(null);
        assertEquals("", result);
    }

    @Test
    public void extractFileNameFromErrorMessage_plainPath_extractsFileName()
    {
        String separator = File.separator;
        String errorMessage = "Error with file" + separator + "path" + separator + "emoji.png";
        String result = FileUtils.extractFileNameFromErrorMessage(errorMessage);
        assertEquals("emoji.png", result);
    }

    @Test
    public void extractFileNameFromErrorMessage_noPath_returnsMessage()
    {
        String errorMessage = "Some error without path";
        String result = FileUtils.extractFileNameFromErrorMessage(errorMessage);
        assertEquals("Some error without path", result);
    }

    @Test
    public void flattenFolder_emptyFolder_returnsEmptyList() throws IOException
    {
        File folder = this.tempFolder.newFolder("empty");
        List<File> result = FileUtils.flattenFolder(folder);
        assertTrue(result.isEmpty());
    }

    @Test
    public void flattenFolder_singleFile_returnsFile() throws IOException
    {
        File folder = this.tempFolder.newFolder("single");
        File file = new File(folder, "test.png");
        Files.createFile(file.toPath());

        List<File> result = FileUtils.flattenFolder(folder);
        assertEquals(1, result.size());
        assertEquals("test.png", result.get(0).getName());
    }

    @Test
    public void flattenFolder_nestedFolders_flattensAll() throws IOException
    {
        File root = this.tempFolder.newFolder("root");
        File sub1 = new File(root, "sub1");
        File sub2 = new File(root, "sub2");
        sub1.mkdir();
        sub2.mkdir();

        Files.createFile(new File(root, "root.png").toPath());
        Files.createFile(new File(sub1, "sub1.png").toPath());
        Files.createFile(new File(sub2, "sub2.png").toPath());

        List<File> result = FileUtils.flattenFolder(root);
        assertEquals(3, result.size());
    }

    @Test
    public void flattenFolder_gitFolder_ignored() throws IOException
    {
        File root = this.tempFolder.newFolder("root");
        File gitFolder = new File(root, ".git");
        gitFolder.mkdir();

        Files.createFile(new File(root, "emoji.png").toPath());
        Files.createFile(new File(gitFolder, "config").toPath());

        List<File> result = FileUtils.flattenFolder(root);
        assertEquals(1, result.size());
        assertEquals("emoji.png", result.get(0).getName());
    }

    @Test
    public void flattenFolder_maxDepthReached_stopsAt8() throws IOException
    {
        File current = this.tempFolder.newFolder("depth0");
        for (int i = 1; i <= 9; i++)
        {
            File next = new File(current, "depth" + i);
            next.mkdir();
            current = next;
        }
        Files.createFile(new File(current, "toodeep.png").toPath());

        File root = this.tempFolder.getRoot().toPath().resolve("depth0").toFile();
        List<File> result = FileUtils.flattenFolder(root);

        boolean containsToodeep = result.stream()
            .anyMatch(f -> f.getName().equals("toodeep.png"));
        assertFalse("File at depth 9 should not be included", containsToodeep);
    }

    @Test
    public void flattenFolder_fileAsInput_returnsFile() throws IOException
    {
        File file = this.tempFolder.newFile("standalone.png");
        List<File> result = FileUtils.flattenFolder(file);
        assertEquals(1, result.size());
        assertEquals("standalone.png", result.get(0).getName());
    }
}
