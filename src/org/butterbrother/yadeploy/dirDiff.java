package org.butterbrother.yadeploy;

import difflib.Delta;
import difflib.DiffUtils;
import difflib.Patch;
import org.apache.tools.ant.DirectoryScanner;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;

/**
 * ��������� ��������� ������������� � ��������� ������ ��� ���������.
 * ���������� ������/���������� �����, ���������� �������� �������� ������.
 */
public class dirDiff {
    private String[] installFilesList;  // ������ ������������� ������ �� ����� ���������
    private String[] deployFileList;    // ������ ������������� ������ �� ������
    private String extractedPath;       // ������ ���� � �������������� �������� ���������
    private String deployPath;          // ������ ���� � �������� ������

    /**
     * �������������
     *
     * @param extracted  ������� � ������������� �������
     * @param deploy     ������� � �������
     * @param watchList  ������ ������ ��� ���������
     * @param ignoreList ������ ������������ ������. ���������� �������������� �� ������� ���������
     */
    public dirDiff(Path extracted, Path deploy, String watchList[], String ignoreList[]) {
        this.extractedPath = extracted.toString();
        this.deployPath = deploy.toString();
        this.installFilesList = getFilesList(extracted.toFile(), watchList, ignoreList);
        this.deployFileList = getFilesList(deploy.toFile(), watchList, ignoreList);
    }

    /**
     * ��������� ��������� �������. ���������� ��������� ������ ������������� ������
     *
     * @param directory  ����������� �������
     * @param watchList  ������ ������������� ������
     * @param ignoreList ������ ������������ ������
     * @return ������ ������ ������������� ������. ����� ����� ����� �������������
     */
    private String[] getFilesList(File directory, String[] watchList, String[] ignoreList) {
        // ��������� �������
        DirectoryScanner dirList = new DirectoryScanner();
        dirList.setBasedir(directory);
        dirList.setIncludes(watchList);
        dirList.setExcludes(ignoreList);
        dirList.setCaseSensitive(false);
        dirList.scan();
        // ����� ������ ������ ��� ���������
        return dirList.getIncludedFiles();
    }

    /**
     * ��������� ��������� ������ � �������� �������� � �������� ������
     * ������������� ����� ������������ � ������������� �������� ����, ���� �������� ������������
     *
     * @throws IOException  ������ ��� ���������� ���������
     */
    public void doRetursiveDiff() throws IOException {
        diffBothAvailableFiles();
        applyNewFiles();
        saveOldFiles();
    }

    /**
     * ������� ������������ �����, ������� ������������ � ����� ���������
     *
     * @throws IOException  ������ ��� ����������� ���������
     */
    private void diffBothAvailableFiles() throws IOException {
        for (String install : installFilesList) {
            for (String original : deployFileList) {
                if (install.equalsIgnoreCase(original)) {
                    Path newFile = Paths.get(extractedPath, install); // ����� ���� �� ���������
                    Path deployFile = Paths.get(deployPath, original);// ������� ���� �� ������
                    // ���������� ���� ������
                    if (! compareMD5digests(newFile, deployFile)) {
                        // ���������� ������� ����� ������ � ����� ������
                        // ��� ��������� ������ ���������� ������ ������ � diff
                        if (isTextFile(install)) {
                            if (!needChangeTextFile(newFile, deployFile))  // ���� ������ ���� �� ����� - �������� ��� �� ������
                                Files.copy(deployFile, newFile);
                        } else {
                            // ��� �������� - ������ ����������
                            if (!needChangeBinaryFile(install))
                                Files.copy(deployFile, newFile);
                        }
                    }
                }
            }
        }
    }

    /**
     * �����������, �������� �� ���� ���������
     *
     * ����������� ������������ �� ���������� �����
     *
     * @param fileName  ��� �����
     * @return          true - ���������, false - ��������
     */
    private boolean isTextFile(String fileName) {
        String textFileTypes[] = { ".txt", ".xml", ".properties" };

        for (String type : textFileTypes)
            if (fileName.toLowerCase().endsWith(type))
                return true;

        return false;
    }

    /**
     * ��������� ����� ������, ������� ����������� � ������ ������������� � ������
     *
     * @throws IOException  ������ ��� ����������� ���������
     */
    private void applyNewFiles() throws IOException {
        boolean found;
        for (String install : installFilesList) {
            found = false;
            for (String original : deployFileList) {
                if (install.equalsIgnoreCase(original)) {
                    found = true;
                }
            }
            if (!found) {
                // ���������� ������������� � ����� �����
                Path newFile = Paths.get(extractedPath, install); // ����� ���� �� ���������
                if (isTextFile(install)) {
                    if (! needAddOrSaveTextFile(newFile, true))
                        Files.delete(newFile);
                } else {
                    if (! needAddOrSaveBinaryFile(install, true))
                        Files.delete(newFile);
                }
            }
        }
    }

    /**
     * ��������� ������ ������, ������� ��� � ����� ������
     *
     * @throws IOException
     */
    private void saveOldFiles() throws IOException {
        boolean found;
        for (String original : deployFileList) {
            found = false;
            for (String install : installFilesList) {
                if (install.equalsIgnoreCase(original)) {
                    found = true;
                }
            }
            if (!found) {
                // ���������� ������������� ���������� ������� �����
                Path oldFile = Paths.get(deployPath, original); // ������� ���� ������
                Path savedFile = Paths.get(extractedPath, original); // ����������� ����
                Path parrent = savedFile.getParent();   // ������������ ������� ������������ �����, ��� ����������� ���������
                if (isTextFile(original)) {
                    if (needAddOrSaveTextFile(oldFile, false)) {
                        // ��������� ��������� ��������� � ��������
                        Files.createDirectories(parrent);
                        // �������� �� ������ � ����� ���������
                        Files.copy(oldFile, savedFile);
                    }
                } else {
                    if (needAddOrSaveBinaryFile(original, false)) {
                        Files.createDirectories(parrent);
                        Files.copy(oldFile, savedFile);
                    }
                }
            }
        }
    }

    /**
     * ������������� ���������� ������ ���� �������� ������� ���������� �����
     *
     * @param changeFile    ���������� ����
     * @param isNewFile     true - ����� ����
     * @return              ������������� ������
     * @throws IOException  ������ ������ ���������� �����
     */
    private boolean needAddOrSaveTextFile(Path changeFile, boolean isNewFile) throws IOException {
        if (isNewFile) {
            System.out.println("Found new text file " + changeFile.getFileName().toString() + ":");
        } else {
            System.out.println("File " + changeFile.getFileName().toString() + " not found in new release:");
        }

        while (true) {
            if (isNewFile) {
                System.out.println("1. Add new file");
                System.out.println("2. Skip new file");
                System.out.println("3. Show new file");
            } else {
                System.out.println("1. Save old file");
                System.out.println("2. Delete old file");
                System.out.println("3. Show old file");
            }
            System.out.print(">> ");
            String userSelect = System.console().readLine();
            try {
                int menuItem = Integer.parseInt(userSelect);
                switch (menuItem) {
                    case 1: return true;
                    case 2: return false;
                    case 3:
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(changeFile)), 4096)) {
                            String buffer;
                            if (isNewFile) {
                                System.out.println("---------- New file: ----------");
                            } else {
                                System.out.println("---------- Old file: ----------");
                            }

                            while ((buffer = reader.readLine())!=null) {
                                System.out.println(buffer);
                            }
                            System.out.println("------------ End --------------");
                        }
                        break;
                }
            } catch (NullPointerException | NumberFormatException err) {
                System.out.println("Invalid select");
            }
        }
    }

    /**
     * ������������� ���������� ������ ���� �������� ������� ��������� �����
     *
     * @param fileName  ���������� ����
     * @param isNewFile true - ����� ����
     * @return          ������������� ������
     */
    private boolean needAddOrSaveBinaryFile(String fileName, boolean isNewFile) {
        if (isNewFile) {
            System.out.println("Found new text file " + fileName + ":");
        } else {
            System.out.println("File " + fileName + " not found in new release:");
        }
        while (true) {
            if (isNewFile) {
                System.out.println("1. Add new file");
                System.out.println("2. Skip new file");
            } else {
                System.out.println("1. Save old file");
                System.out.println("2. Delete old file");
            }
            System.out.print(">> ");
            String userSelect = System.console().readLine();
            try {
                int menuItem = Integer.parseInt(userSelect);
                switch (menuItem) {
                    case 1: return true;
                    case 2: return false;
                }
            } catch (NullPointerException | NumberFormatException err) {
                System.out.println("Invalid select");
            }
        }
    }

    /**
     * ������������� ���������� ��������� �����
     *
     * @param fileName  ��� �����
     * @return          ������������� ����������
     */
    private boolean needChangeBinaryFile(String fileName) {
        System.out.println("Found difference in binary file " + fileName + ":");
        while (true) {
            System.out.println("1. Apply new file");
            System.out.println("2. Save old file");
            System.out.print(">> ");
            String userSelect = System.console().readLine();
            try {
                int menuItem = Integer.parseInt(userSelect);
                switch (menuItem) {
                    case 1: return true;
                    case 2: return false;
                }
            } catch (NullPointerException | NumberFormatException err) {
                System.out.println("Invalid select");
            }
        }
    }

    /**
     * ������������� ���������� ���������� �����
     *
     * @param newFile       ����� ����
     * @param deployFile    ���� �� ������
     * @return              ������������� ������. true - ��������� ����� ����. false - �������� ���� ��
     * ������ � ������������� �������
     * @throws IOException
     */
    private boolean needChangeTextFile(Path newFile, Path deployFile) throws IOException {
        System.out.println("Found difference in text file " + newFile.getFileName().toString() + ":");
        while (true) {
            System.out.println("1. Apply new file");
            System.out.println("2. Save old file");
            System.out.println("3. Show difference");
            System.out.print(">> ");
            String userSelect = System.console().readLine();
            try {
                int menuItem = Integer.parseInt(userSelect);
                switch (menuItem) {
                    case 1: return true;
                    case 2: return false;
                    case 3: showTextFileDiff(newFile, deployFile);
                        break;
                }
            } catch (NullPointerException | NumberFormatException err) {
                System.out.println("Invalid select");
            }
        }
    }

    /**
     * ���������� ������� � ���� diff-� ����� ����� ���������� �������
     *
     * @param newFile       ����� ����
     * @param deployFile    ������� ����
     * @throws IOException  ������ ������ ������ �� ������
     */
    private void showTextFileDiff(Path newFile, Path deployFile) throws IOException {
        LinkedList<String> originalFile = readFileToList(deployFile);
        LinkedList<String> overrideFile = readFileToList(newFile);
        Patch difference = DiffUtils.diff(originalFile, overrideFile);
        System.out.println("---------- Difference: ----------");
        for (Delta item : difference.getDeltas()) {
            System.out.println(item);
        }
        System.out.println("------------- End ---------------");
    }

    /**
     * ��������� �� ����� � ��������� ������
     *
     * @param file  ����
     * @return      ��������� ������
     * @throws IOException  ������ ������ �����
     */
    private LinkedList<String> readFileToList(Path file) throws IOException {
        LinkedList<String> result = new LinkedList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file, StandardOpenOption.READ)), 4096)) {
            String buffer;
            while ((buffer = reader.readLine()) != null)
                result.add(buffer);
        }

        return result;
    }

    /**
     * ���������� ����� �� MD5-����
     *
     * @param one ������ ����
     * @param two ������ ����
     * @return ��������� ���� ���
     * @throws IOException ������ �����-������ ��� ���������� ������ �����, ��� ���������
     */
    private boolean compareMD5digests(Path one, Path two) throws IOException {
        byte oneDigest[] = getMD5digest(one);
        byte twoDigest[] = getMD5digest(two);
        if (oneDigest.length == 0 || twoDigest.length == 0) return false;
        if (oneDigest.length != twoDigest.length) return false;
        for (int i = 0; i < oneDigest.length; i++)
            if (oneDigest[i] != twoDigest[i]) return false;

        return true;
    }

    /**
     * ������� MD5-���� �����
     *
     * @param file ������� ����
     * @return MD5-���
     * @throws IOException ������ �����-������ ��� ������ �����
     */
    private byte[] getMD5digest(Path file) throws IOException {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            try (SeekableByteChannel reader = Files.newByteChannel(file, StandardOpenOption.READ)) {
                ByteBuffer bytes = ByteBuffer.allocate(4096);
                int count;
                do {
                    count = reader.read(bytes);
                    if (count > 0) {
                        bytes.rewind();
                        md5.update(bytes);
                    }
                } while (count > 0);
            }
            return md5.digest();
        } catch (NoSuchAlgorithmException ignore) {
        }

        return new byte[0];
    }

}
