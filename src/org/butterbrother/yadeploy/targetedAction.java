package org.butterbrother.yadeploy;

import org.apache.tools.ant.DirectoryScanner;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Calendar;
import java.util.Formatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ���������������� ���������� ��������
 */
public class targetedAction implements staticValues {
    public static void doBackup(configStorage settings, ticket direction) {
        boolean debug = settings.isDebug();
        // ������ ��� �����
        // ������� �������� ������� ������. ���� ��� ���, ���������� ��� ��������
        // ������
        StringBuilder fileName = new StringBuilder();
        if (settings.getReleaseName().isEmpty()) {
            fileName.append(direction.getDeployPath().getFileName());
        } else {
            fileName.append(settings.getReleaseName());
        }
        System.out.println("Use backup prefix " + fileName);

        // ���������� ���� � ����� ��������
        fileName.append("_").append(genDateTimePostfix());
        // ���������� ���������� �����
        fileName.append(".zip");

        // ��������� ������ ����
        Path backupFile = Paths.get(direction.getBackupsPath().toString(), fileName.toString());
        System.out.println("Backup is " + backupFile);

        // ��������� ���������
        try (ZipOutputStream zipFile = new ZipOutputStream(
                new BufferedOutputStream(
                        new FileOutputStream(backupFile.toFile(), false), 4096 ))) {
            byte buffer[] = new byte[4096];
            int length;
            // ���������
            DirectoryScanner dirscan = new DirectoryScanner();
            dirscan.setBasedir(direction.getDeployPath().toFile());
            dirscan.setCaseSensitive(true);
            dirscan.scan();
            // �������� ������ ������ � ���������
            String[] dirs = dirscan.getIncludedDirectories();
            String[] files = dirscan.getIncludedFiles();

            // ������ ��������� ���������
            for (String dir : dirs) {
                if (dir.isEmpty()) continue;    // ���������� ���������� ��������� �������� - ��� ������
                ZipEntry zipped = new ZipEntry(dir + "/");
                // ������� ����-����� �����������
                FileTime modTime = Files.getLastModifiedTime(Paths.get(direction.getDeployPath().toString(), dir));
                System.out.println("Create dir in ZIP: " + zipped.getName());
                zipped.setTime(modTime.toMillis());
                zipFile.putNextEntry(zipped);
                zipFile.closeEntry();
            }

            // ������� �����
            for (String file : files) {
                ZipEntry zipped = new ZipEntry(file);
                // ������� ����-����� ����������� �����
                FileTime modTime = Files.getLastModifiedTime(Paths.get(direction.getDeployPath().toString(), file));
                zipped.setTime(modTime.toMillis());
                zipFile.putNextEntry(zipped);
                System.out.println("Compress file " + zipped.getName());
                try (BufferedInputStream inpDir =
                             new BufferedInputStream(
                                     new FileInputStream(
                                             new File(direction.getDeployPath().toFile(), file)), 4096)) {
                    while ((length = inpDir.read(buffer)) > 0)
                        zipFile.write(buffer, 0, length);
                } catch (IOException inpDirReadErr) {
                    System.err.println("Unable compress file " + file + ": " + inpDirReadErr);
                    if (debug) inpDirReadErr.printStackTrace();
                }
                zipFile.closeEntry();
            }
        } catch (FileNotFoundException ignore) {
        } catch (IOException err) {
            System.err.println("Compression error: " + err);
            if (debug) err.printStackTrace();
            System.exit(EXIT_BACKUP_ERROR);
        }
    }

    /**
     * ���������� �������� � ������� ����� � ��������
     *
     * @return  ���� � ����� � ������� ����-��-��_��-��
     */
    private static String genDateTimePostfix() {
        return new Formatter().format("%TY-%<Tm-%<Td_%<TH-%<TM", Calendar.getInstance()).toString();
    }
}
