package org.butterbrother.yadeploy;

import org.apache.tools.ant.DirectoryScanner;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ������ ��������� �� ������
 */
public class fileChangeStatistic {
    // ����� ������ ����, ����� ������� �������� � ��., ������� ��� ��������� � ������
    // ������ ����� � ��. ������ ����� ����������� ��������� ��������
    private long oldestFile = Long.MAX_VALUE;
    // ����� ����� ����. � ����������������� - ����� ���������� ��������. ������� ���
    // ��������� � ����� ������� ����� ������ ���� � ���������� ��������� ���������
    private long newestFile = 0;
    // ����� ��������������� ���� ����������� ������, ����-����� ������
    private TreeMap<Long, Integer> mostModTime = new TreeMap<>();
    // ���� ����������� ������������ �������� ���� ������
    private long parentModTime = 0;

    public fileChangeStatistic() {
    }

    /**
     * ����������� ��������� zip-������.
     * <p/>
     * ��������� ��������� ������� �� ���� ��������� ����������.
     * ���������� ��, � ������� ������� ������� ����������� ����� � ������.
     *
     * @param zipFile zip-�����
     * @return ���������, ���������� ��� ��������
     * @throws IOException ���� ������ �� �������/������ �������� �����
     */
    public static Charset detectZipEncoding(Path zipFile) throws IOException {
        // ������������ ��� ����� ������ �������
        String incorrectCharacters[] = {"\"", "*", ":", "<", ">", "?", "|"};
        // �������� ��� ��������� ���������
        LinkedHashMap<String, Charset> allCharsets = new LinkedHashMap<>();
        // ��������� � ������ ����� ��������� - ��� ��������� DOS, ������� � ��������� Windows
        allCharsets.put("ru-dos", Charset.forName("CP866"));
        allCharsets.put("current-system", Charset.defaultCharset());
        allCharsets.put("ru-win", Charset.forName("WINDOWS-1251"));
        allCharsets.put("utf8_ru", Charset.forName("UTF-8"));
        // ����� �� ������ ��������� ������ ��� ��������� ����������
        allCharsets.putAll(Charset.availableCharsets());
        // � ������ ������� ����������� ��� ����� � ������� �����������
        for (Map.Entry<String, Charset> probe : allCharsets.entrySet()) {
            encodeTry:
            {
                try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile), 4096), probe.getValue())) {
                    // ������������ �����
                    ZipEntry compressed;
                    while ((compressed = zip.getNextEntry()) != null) {
                        for (String item : incorrectCharacters) {
                            if (compressed.getName().contains(item)) {
                                // ���� ��� ����� �������� �������� ������� - ����������
                                zip.closeEntry();
                                break encodeTry;
                            }
                        }
                        zip.closeEntry();
                    }
                } catch (IllegalArgumentException ignore) {
                    // ��� ���������� ����������� ����������. ����� ���
                    continue;
                }
                return probe.getValue();
            }
        }

        // ���� ������ �� �����
        throw new IOException("Unable detect ZIP file encoding");
    }

    /**
     * ���������� ����� � ����������
     *
     * @param fileModTime ���� ����������� � ��.
     */
    public void addFile(long fileModTime) {
        if (fileModTime <= 0) return;   // ��������� �����, ��� ������� �� ������� �������� ���� �����������

        // ���� � ������ ���
        boolean found = false;
        for (Map.Entry<Long, Integer> item : mostModTime.entrySet()) {
            if (item.getKey() == fileModTime) {
                // ���� ������� - ���������� � ����������
                item.setValue(item.getValue() + 1);
                found = true;
            }
        }
        if (!found)
            mostModTime.put(fileModTime, 1);

        // ���������� � �������� ������
        if (fileModTime > newestFile)
            newestFile = fileModTime;
        if (fileModTime < oldestFile)
            oldestFile = fileModTime;
    }

    /**
     * ���������� ����� � ����������
     *
     * @param file ����
     * @throws IOException ������ ��� ��������� ����-������� �����������
     */
    public void addFile(Path file) throws IOException {
        addFile(Files.getLastModifiedTime(file).toMillis());
    }

    /**
     * ������������� ���� � ����� ����������� ������������ �������� ���� ������.
     *
     * @param fileModTime ���� ����������� � ��.
     */
    public void setParentModTime(long fileModTime) {
        parentModTime = fileModTime;
    }

    /**
     * ����������� ���������� ��� �������� �������, ������ ��� �������,
     * ����������� � ��������. ����� �� �������� ������� ����������
     *
     * @param file    ����
     * @param ignored ������ ����������
     * @param deleted ������ ��������� ������
     * @throws IOException ������ �����-������ ��� ��������
     */
    public void calculatePath(Path file, String[] ignored, String[] deleted) throws IOException {
        // ��������� ������ ��� �������� ����������
        LinkedList<String> comboIgnoreList = new LinkedList<>();
        if (ignored != null && ignored.length > 0)
            comboIgnoreList.addAll(Arrays.asList(ignored));
        if (deleted != null && deleted.length > 0)
            comboIgnoreList.addAll(Arrays.asList(deleted));

        String comboIgnore[] = comboIgnoreList.toArray(new String[comboIgnoreList.size()]);

        // ������������ ���������� � ��������
        if (Files.exists(file)) {
            // ��� �������� ��������
            setParentModTime(file);
            // � �������� ���...
            DirectoryScanner deployList = new DirectoryScanner();
            deployList.setBasedir(file.toString());
            if (comboIgnore.length > 0)
                deployList.setExcludes(comboIgnore);
            deployList.scan();
            // ...���������
            for (String item : deployList.getIncludedDirectories()) {
                if (item.isEmpty()) continue; // ���������� �������� �������
                Path includedDir = Paths.get(file.toString(), item);
                addFile(includedDir);
            }
            // ...� ������
            for (String item : deployList.getIncludedFiles()) {
                Path includedFile = Paths.get(file.toString(), item);
                addFile(includedFile);
            }
        }
    }

    /**
     * ����������� ���������� ��� zip-������ �������, ������� ��� zip-�����
     * ����� �� �������� ������� ����������.
     *
     * @param zipFile zip-�����
     * @throws IOException ������ �����-������ ��� ��������
     */
    public void calculateZip(Path zipFile) throws IOException {
        setParentModTime(zipFile);

        Charset zipEncoding = detectZipEncoding(zipFile);
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream((Files.newInputStream(zipFile)), 4096), zipEncoding)) {
            ZipEntry archive;
            while ((archive = zip.getNextEntry()) != null) {
                // ������������ �������������� ������
                addFile(archive.getTime());
                // ������������ ����� �����
                zip.closeEntry();
            }
        }
    }

    /**
     * ������������� ���� � ����� ����������� ������������ �������� ���� ������.
     *
     * @param file ������� ���� ����
     * @throws IOException ������ ��� ��������� ����-������� �����������
     */
    public void setParentModTime(Path file) throws IOException {
        setParentModTime(Files.getLastModifiedTime(file).toMillis());
    }

    /**
     * ����������� ���������� �� ������
     */
    public void showStatistic() {
        Formatter outStat = new Formatter(System.out);

        // ���� ����������� ������-������������ ��������
        outStat.format("Container modification date: ");
        if (this.parentModTime <= 0) {
            outStat.format("not available\n");
        } else {
            Calendar parentTime = Calendar.getInstance();
            parentTime.setTimeInMillis(this.parentModTime);
            outStat.format("%TY-%<Tm-%<Td %<TH:%<TM\n", parentTime);
        }

        // ����� ������ ����
        outStat.format("Oldest file modification date: ");
        if (this.oldestFile == Long.MAX_VALUE || this.oldestFile <= 0) {
            outStat.format("not available\n");
        } else {
            Calendar oldestFile = Calendar.getInstance();
            oldestFile.setTimeInMillis(this.oldestFile);
            outStat.format("%TY-%<Tm-%<Td %<TH:%<TM\n", oldestFile);
        }

        // ����� ����� ����
        outStat.format("Newest file modification date: ");
        if (this.newestFile <= 0) {
            outStat.format("not available\n");
        } else {
            Calendar newestFile = Calendar.getInstance();
            newestFile.setTimeInMillis(this.newestFile);
            outStat.format("%TY-%<Tm-%<Td %<TH:%<TM\n", newestFile);
        }

        // ������ �����, ����������
        outStat.format("Other files modification date count:\n");
        for (Map.Entry<String, Integer> stat : getAllFilesModStatistic().entrySet()) {
            outStat.format("-- %s - %d\n", stat.getKey(), stat.getValue());
        }
    }

    /**
     * ��������� ���������� �� ������, ���� ������������ � �������� �� ������
     *
     * @return ����������
     */
    public TreeMap<String, Integer> getAllFilesModStatistic() {
        boolean found;
        TreeMap<String, Integer> result = new TreeMap<>();
        // ������������ ������������� ���� � ����, ����� � ��������������� ������
        // ������� ���������� ������ � ��� ���������� � ����������
        for (Map.Entry<Long, Integer> statValue : mostModTime.entrySet()) {
            // ����������� � ���������
            Calendar dateTime = Calendar.getInstance();
            dateTime.setTimeInMillis(statValue.getKey());
            // ����� � ������, ������� ���� �� ������
            String formattedDate = new Formatter().format("%TY-%<Tm-%<Td %<TH:%<TM", dateTime).toString();

            // ����� ���� � �����������
            found = false;
            for (Map.Entry<String, Integer> comp : result.entrySet()) {
                if (comp.getKey().equals(formattedDate)) {
                    // ���� ��� ���� - ���������� � ���������
                    comp.setValue(comp.getValue() + statValue.getValue());
                    found = true;
                    break;
                }
            }

            // ����� ��������� ����� ������
            if (!found)
                result.put(formattedDate, statValue.getValue());
        }

        return result;
    }
}
