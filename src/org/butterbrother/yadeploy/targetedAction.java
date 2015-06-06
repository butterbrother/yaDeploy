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
 * Непосредственное выполнение действий
 */
public class targetedAction implements staticValues {
    public static void doBackup(configStorage settings, ticket direction) {
        boolean debug = settings.isDebug();
        // Создаём имя файла
        // Вначале получаем префикс бекапа. Если его нет, используем имя каталога
        // деплоя
        StringBuilder fileName = new StringBuilder();
        if (settings.getReleaseName().isEmpty()) {
            fileName.append(direction.getDeployPath().getFileName());
        } else {
            fileName.append(settings.getReleaseName());
        }
        System.out.println("Use backup prefix " + fileName);

        // Дописываем дату и время создания
        fileName.append("_").append(genDateTimePostfix());
        // Дописываем расширение файла
        fileName.append(".zip");

        // Формируем полный путь
        Path backupFile = Paths.get(direction.getBackupsPath().toString(), fileName.toString());
        System.out.println("Backup is " + backupFile);

        // Выполняем архивацию
        try (ZipOutputStream zipFile = new ZipOutputStream(
                new BufferedOutputStream(
                        new FileOutputStream(backupFile.toFile(), false), 4096 ))) {
            byte buffer[] = new byte[4096];
            int length;
            // Сканируем
            DirectoryScanner dirscan = new DirectoryScanner();
            dirscan.setBasedir(direction.getDeployPath().toFile());
            dirscan.setCaseSensitive(true);
            dirscan.scan();
            // Получаем список файлов и каталогов
            String[] dirs = dirscan.getIncludedDirectories();
            String[] files = dirscan.getIncludedFiles();

            // Создаём структуру каталогов
            for (String dir : dirs) {
                if (dir.isEmpty()) continue;    // Пропускаем добавление корневого каталога - имя архива
                ZipEntry zipped = new ZipEntry(dir + "/");
                // Получаю дату-время модификации
                FileTime modTime = Files.getLastModifiedTime(Paths.get(direction.getDeployPath().toString(), dir));
                System.out.println("Create dir in ZIP: " + zipped.getName());
                zipped.setTime(modTime.toMillis());
                zipFile.putNextEntry(zipped);
                zipFile.closeEntry();
            }

            // Сжимаем файлы
            for (String file : files) {
                ZipEntry zipped = new ZipEntry(file);
                // Получаю дату-время модификации файла
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
     * Генерирует постфикс с текущей датой и временем
     *
     * @return  Дата и время в формате ГГГГ-ММ-ДД_ЧЧ-МИ
     */
    private static String genDateTimePostfix() {
        return new Formatter().format("%TY-%<Tm-%<Td_%<TH-%<TM", Calendar.getInstance()).toString();
    }
}
