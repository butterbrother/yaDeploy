package org.butterbrother.yadeploy;

import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.DirectoryScanner;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        // Префикс - имя каталога деплоя
        StringBuilder fileName = new StringBuilder();
        fileName.append(direction.getSourceName());
        System.out.println("Use backup prefix " + fileName);

        // Дописываем дату и время создания
        fileName.append("_").append(genDateTimePostfix());

        // Дальнейшее поведение зависит от типа бекапа
        String type;
        try {
            type = settings.getParameter(BACKUPS_SECTION, BACKUPS_TYPE).toLowerCase();
        } catch (ParameterNotFoundException err) {
            System.out.println("Use default backup - compression to zip.");
            System.out.println("To override this, set \"[" + BACKUPS_SECTION + "]\"->\"" + BACKUPS_TYPE + "\" to another.");
            type = "zip";
        }

        // Получаем список игнорируемых файлов
        String[] ignored;
        try {
            ignored = settings.getSepatatedParameter(BACKUPS_SECTION, BACKUPS_IGNORE);
            // Отображаем список, если он есть
            System.out.println("This files will be ignored:");
            StringBuilder ignoreList = new StringBuilder();
            for (String ignoredItem : ignored)
                ignoreList.append("- ").append(ignoredItem).append("\n");
            System.out.println(ignoreList);
        } catch (ParameterNotFoundException err) {
            System.out.println("No ignore files.");
            ignored = new String[0];
        }
        // Сканируем
        DirectoryScanner dirScan = new DirectoryScanner();
        dirScan.setBasedir(direction.getSourceFile());
        dirScan.setCaseSensitive(true);
        // Добавляем исключения
        dirScan.setExcludes(ignored);
        dirScan.scan();
        // Получаем список файлов и каталогов
        String[] dirs = dirScan.getIncludedDirectories();
        String[] files = dirScan.getIncludedFiles();

        if (type.equals("zip")) {
            // Дописываем расширение файла
            fileName.append(".zip");

            // Формируем полный путь
            Path backupFile = Paths.get(direction.getDestinationFullName(), fileName.toString());
            System.out.println("Backup is " + backupFile);

            // Выполняем архивацию
            try (ZipOutputStream zipFile = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(backupFile), 4096))) {
                byte buffer[] = new byte[4096];
                int length;

                // Создаём структуру каталогов
                for (String dir : dirs) {
                    if (dir.isEmpty()) continue;    // Пропускаем добавление корневого каталога - имя архива
                    ZipEntry zipped = new ZipEntry(dir + "/");
                    // Получаю дату-время модификации
                    FileTime modTime = Files.getLastModifiedTime(Paths.get(direction.getSourceFullName(), dir));
                    System.out.println("Create dir in ZIP: " + zipped.getName());
                    zipped.setTime(modTime.toMillis());
                    zipFile.putNextEntry(zipped);
                    zipFile.closeEntry();
                }

                // Сжимаем файлы
                for (String file : files) {
                    ZipEntry zipped = new ZipEntry(file);
                    // Получаю дату-время модификации файла
                    FileTime modTime = Files.getLastModifiedTime(Paths.get(direction.getSourceFullName(), file));
                    zipped.setTime(modTime.toMillis());
                    zipFile.putNextEntry(zipped);
                    System.out.println("Compress file " + zipped.getName());
                    try (BufferedInputStream inpDir = new BufferedInputStream(Files.newInputStream(Paths.get(direction.getSourceFullName(), file)), 4096)) {
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
        } else {
            // Формируем полный путь
            Path backupDir = Paths.get(direction.getDestinationFullName(), fileName.toString());
            System.out.println("Backup path is " + backupDir);

            // Если уже существует аналогичный каталог - удаляем
            if (Files.exists(backupDir)) {
                try {
                    FileUtils.deleteDirectory(backupDir.toFile());
                } catch (IOException deleteError) {
                    System.err.println("Unable to delete some name backup directory " + backupDir.getFileName() + ": " + deleteError);
                    System.exit(EXIT_BACKUP_ERROR);
                }
            }

            // Создаём каталог с бекапом
            try {
                Files.createDirectories(backupDir);
            } catch (IOException ioError) {
                System.err.println("Unable to create backup directory " + backupDir.getFileName() + ": " + ioError);
                if (debug) ioError.printStackTrace();
                System.exit(EXIT_BACKUP_ERROR);
            }

            // Воссоздаём структуру каталога деплоя
            for (String dir: dirs) {
                try {
                    Files.createDirectories(Paths.get(backupDir.toString(), dir));
                } catch (IOException ioErr) {
                    System.err.println("Unable to create " + dir + ": " + ioErr);
                }
            }

            // Последовательно копируем файлы
            for (String file: files) {
                try {
                    Files.copy(
                            Paths.get(direction.getSourceFullName(), file),
                            Paths.get(backupDir.toString(), file)
                    );
                } catch (IOException ioErr) {
                    System.err.println("Unable to backup " + file + ": " + ioErr);
                }
            }
        }
    }

    /**
     * Генерирует постфикс с текущей датой и временем
     *
     * @return Дата и время в формате ГГГГ-ММ-ДД_ЧЧ-МИ
     */
    private static String genDateTimePostfix() {
        return new Formatter().format("%TY-%<Tm-%<Td_%<TH-%<TM", Calendar.getInstance()).toString();
    }
}
