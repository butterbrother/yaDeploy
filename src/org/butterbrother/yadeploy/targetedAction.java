package org.butterbrother.yadeploy;

import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.butterbrother.selectFSItem.selectItem;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Непосредственное выполнение действий
 */
public class targetedAction implements staticValues {
    /**
     * Выполнение бекапа
     *
     * @param settings  Настройки из файла настроек
     * @param direction Разрешение и направление
     */
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
            for (String dir : dirs) {
                try {
                    Files.createDirectories(Paths.get(backupDir.toString(), dir));
                } catch (IOException ioErr) {
                    System.err.println("Unable to create " + dir + ": " + ioErr);
                }
            }

            // Последовательно копируем файлы
            for (String file : files) {
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
     * Разворачивает релиз/бекап из архива в деплой
     *
     * @param settings  Настройки из файла настроек
     * @param direction Направление и разрешение
     * @param isInstall true - установка, false - восстановление из бекапа
     */
    public static void doDeploy(configStorage settings, ticket direction, boolean isInstall) {
        // Статистические данные
        // По архиву
        fileChangeStatistic archiveStat = new fileChangeStatistic();
        // По деплою
        fileChangeStatistic deployStat = new fileChangeStatistic();

        Path installPath = getInstallFilePath(settings, direction, isInstall);    // Новый релиз, либо бекап

        // Основная процедура деплоя
        // Получаем дату-время модификации источника
        try {
            archiveStat.setParentModTime(installPath);
        } catch (IOException statErr) {
            System.err.println("Warn: unable to get source modification time");
        }

        // Вначале создаём каталог временных файлов. Он однозначно будет удалён впоследствие
        Path currentTempDir = Paths.get(direction.getTemporariesFullName(), "deploy_" + genDateTimePostfix());
        try {
            Files.createDirectories(currentTempDir);
        } catch (IOException err) {
            installRestoreError("create temporary directory I/O error: ", err, isInstall);
        }

        // Далее распаковываем, если это архив. Либо копируем, если это каталог
        if (Files.isRegularFile(installPath)) {
            try {
                // Рассчитываем статистику
                archiveStat.calculateZip(installPath);
                // Распаковываем zip
                extractArchive(installPath, currentTempDir);
            } catch (IOException mainUnzipError) {
                // Любая ошибка распаковки - прерывающая
                installRestoreError("unable unzip archive", mainUnzipError, isInstall);
            }
        } else if (Files.isDirectory(installPath)) {
            try {
                archiveStat.calculatePath(installPath, new String[0], new String[0]);
                // Копируем файлы
                recursiveCopyDir(installPath, currentTempDir);
            } catch (IOException copyError) {
                installRestoreError("unable to copy extracted archive", copyError, isInstall);
            }
        }

        // Скопировано и распаковано во временный каталог
        // Получаем данные для удаления и игнорирования при установке
        String ignoreList[] = getIgnoreList(settings);
        String deleteList[] = getDeleteList(settings);

        // Обрабатываем статистику деплоя
        try {
            deployStat.calculatePath(direction.getDestinationPath(), ignoreList, deleteList);
        } catch (IOException err) {
            System.err.println("Warn: unable to get deploy modification time statistic");
        }
    }

    /**
     * Получение списка игнорируемых файлов из конечного деплоя
     *
     * @param settings  Файл настроек
     * @return          Список игнорируемых файлов. Если их нет - массив будет с 0 элементов.
     */
    private static String[] getIgnoreList(configStorage settings) {
        try {
            return settings.getSepatatedParameter(DEPLOY_SECTION, DEPLOY_IGNORE);
        } catch (ParameterNotFoundException exp) {
            System.out.println("Ignore deploy list not set and will not be used");
            return new String[0];
        }
    }

    /**
     * Получение списка удаляемых файлов из конечного деплоя
     *
     * @param settings  Файл настроек
     * @return          Список игнорируемых файлов. Если их нет - массив будет с 0 элементов.
     */
    private static String[] getDeleteList(configStorage settings) {
        try {
            return settings.getSepatatedParameter(DEPLOY_SECTION, DEPLOY_DELETE);
        } catch (ParameterNotFoundException exp) {
            System.out.println("Delete list into deploy not set and will not be used");
            return new String[0];
        }
    }

    /**
     * Получение каталога/файла релиза
     *
     * @param settings      Настройки из файла настроек
     * @param direction     Файлы источника и получателя, разрешение
     * @param isInstall     true - установка, false - восстановление
     * @return              Файл/каталог с релизом либо бекапом
     */
    private static Path getInstallFilePath(configStorage settings, ticket direction, boolean isInstall) {
        // Если указана версия релиза/бекапа, то сначала попытаемся найти её
        Path installPath = null;
        if (!settings.getReleaseName().isEmpty()) {
            DirectoryScanner installFind = new DirectoryScanner();
            installFind.setBasedir(direction.getSourceFile());
            // Если это установка и есть маска, то применяем её
            if (isInstall) {
                try {
                    String filter[] = {settings.getParameter(RELEASES_SECTION, RELEASES_REGEXP)};
                    installFind.setIncludes(filter);
                } catch (ParameterNotFoundException ignore) {
                    System.out.println("Releases filter not set, search in all files");
                }
            } else {
                // Иначе маской становится имя каталога деплоя
                String filter[] = {direction.getDestinationPath().getFileName().toString()};
                installFind.setIncludes(filter);
            }
            installFind.scan();
            // Добавляем все файлы и каталоги
            LinkedList<String> list = new LinkedList<>();
            list.addAll(Arrays.asList(installFind.getIncludedFiles()));
            list.addAll(Arrays.asList(installFind.getIncludedDirectories()));
            {
                Iterator<String> item = list.iterator();

                // Выполняем очистку списка
                while (item.hasNext()) {
                    String value = item.next();

                    // Исключаем подкаталоги и файлы 2го уровня и выше
                    if (value.contains("/") && value.indexOf('/') != value.length() - 1) {
                        item.remove();
                        continue;
                    }

                    // Исключаем так же файлы и каталоги, не содержащие имя релиза
                    if (!value.contains(settings.getReleaseName())) {
                        item.remove();
                        continue;
                    }

                    // Если это файл, то он должен быть zip-архивом. Другие не поддерживаются
                    // Об этом надо объявить
                    Path releaseFile = Paths.get(direction.getSourceFullName(), value);
                    if (Files.isRegularFile(releaseFile)) {
                        if (! validateFileType(releaseFile)) {
                            System.err.println("File " + value + " not is zip-archive, unsupported, skip from list");
                            item.remove();
                        }
                    }
                }
            }
            // В остатке только каталоги либо zip-файлы, содержащие релиз. Либо распакованные релизы

            if (list.size() > 1) {
                // Если нашлось несколько релизов, то нужно предложить выбор
                boolean noselect = true;
                int index, menuitem;
                while (noselect) {
                    if (isInstall) {
                        System.out.println("Found more what one releases, select one:");
                    } else {
                        System.out.println("Found more what one backups, select one:");
                    }

                    index = 0;
                    for (String item : list) {
                        System.out.println(index++ + " " + item);
                    }
                    System.out.print("[q - exit] >> ");
                    try {
                        String userSelect = System.console().readLine();
                        // q - отмена и выход
                        if (userSelect.equalsIgnoreCase("q")) {
                            System.err.println("Cancelled by user");
                            System.exit(EXIT_NORMAL);
                        }
                        menuitem = Integer.parseInt(userSelect);
                        installPath = Paths.get(direction.getSourceFullName(), list.get(menuitem - 1));
                        noselect = false;
                    } catch (IndexOutOfBoundsException | NumberFormatException err) {
                        System.err.println("No valid number");
                    }
                }
            } else if (list.size() == 1) {
                // Если нашёлся один файл/каталог - выбираем его
                installPath = Paths.get(direction.getSourceFullName(), list.get(0));
            } else {
                // Если не нашлось ни одного - завершение работы
                installRestoreError("No releases found", isInstall);
            }
        }

        // Если релиз/бекап не был выбран, то выводим выбор файлов
        if (installPath == null) {
            try {
                installPath = selectItem.selectFile(direction.getSourceFullName(), "*", false);
                // null только если пользователь ввёл q и отказался от выбора
                if (installPath == null) {
                    System.err.println("Cancelled by user");
                    System.exit(EXIT_NORMAL);
                }
                // Проверяем, что если выбран файл - то он zip
                if (! validateFileType(installPath))
                    installRestoreError("unsupported file. Must be a zip-file or unpacked directory", isInstall);
            } catch (IOException err) {
                installRestoreError("I/O error", err, isInstall);
            }
        }

        if (installPath == null) {
            installRestoreError("no archive selected", isInstall);
            System.exit(EXIT_GENERAL_ERROR);
        }

        return installPath;
    }


    /**
     * Проверка верного типа файла, годного для деплоя.
     * Приложение поддерживает только zip-архивы:
     * .zip и .war
     *
     * @param releaseFile   Проверяемый файл
     * @return              Поддерживаемость файла приложением
     */
    private static boolean validateFileType(Path releaseFile) {
        if (Files.isDirectory(releaseFile)) return true;    // Каталоги разрешены
        String mimeType;
        try {
            mimeType = Files.probeContentType(releaseFile);
        } catch (IOException err) {
            mimeType = null;
        }
        String fileStringPath = releaseFile.toString().toLowerCase();
        if (mimeType != null) {
            if (mimeType.contains("application/zip"))
                return true;
        } else {
            // Иногда получение content-type возвращает null
            // тогда проверяем по расширению файла
            if (fileStringPath.endsWith(".zip") || (fileStringPath.endsWith(".war"))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Рекурсивное копирование каталогов
     *
     * @param source      Источник
     * @param destination Назначение
     * @throws IOException Ошибка при копировании
     */
    private static void recursiveCopyDir(Path source, Path destination) throws IOException {
        // Копируем файлы
        DirectoryScanner copyFiles = new DirectoryScanner();
        copyFiles.setBasedir(source.toFile());
        copyFiles.scan();
        Formatter progressBar = new Formatter(System.out);
        progressBar.format("Copy directory recursively:\n");
        // Воссоздаём структуру
        for (String item : copyFiles.getIncludedDirectories()) {
            progressBar.format("-- %s: %s", "Create dir\n", item);
            Files.createDirectories(Paths.get(destination.toString(), item));
        }
        // Копируем файлы
        for (String item : copyFiles.getIncludedFiles()) {
            progressBar.format("-- %s: %s", "Copy file\n", item);
            Path sourceFile = Paths.get(source.toString(), item);
            Path targetFile = Paths.get(destination.toString(), item);
            Files.copy(sourceFile, targetFile);
        }
    }

    /**
     * Распаковка архива в указанный каталог
     *
     * @param zipFile        Архив
     * @param destinationDir Целевой каталог
     * @throws IOException Ошибка при разорхивации
     */
    private static void extractArchive(Path zipFile, Path destinationDir) throws IOException {
        Charset zipEncoding = fileChangeStatistic.detectZipEncoding(zipFile);
        Formatter progressBar = new Formatter(System.out);
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile), 4096), zipEncoding)) {
            progressBar.format("Extract archive:\n");
            int length;
            byte buffer[] = new byte[4096];
            ZipEntry archive;
            while ((archive = zip.getNextEntry()) != null) {
                if (archive.isDirectory()) {
                    progressBar.format("-- Create dir: %s\n", archive.getName());
                    // Создаём структуру каталогов
                    Path extractedDir = Paths.get(destinationDir.toString(), archive.getName());
                    Files.createDirectories(extractedDir);
                } else {
                    // Распаковываем файл
                    Path extractedFile = Paths.get(destinationDir.toString(), archive.getName());
                    // Проверяем, существует ли родительский каталог. Воссоздаём структуру, если нет
                    Path parrent = extractedFile.getParent();
                    if (Files.notExists(parrent))
                        Files.createDirectories(parrent);
                    // Пишем сам файл
                    try (BufferedOutputStream unzipped = new BufferedOutputStream(Files.newOutputStream(extractedFile), 4096)) {
                        while ((length = (zip.read(buffer))) > 0)
                            unzipped.write(buffer, 0, length);
                    }
                }
                zip.closeEntry();
            }
        }
        System.out.println("Extract complete");
    }

    /**
     * Ошибка установки/восстановления. С указанием причины и исключения
     *
     * @param cause     Причина отказа
     * @param err       Исключение
     * @param isInstall true - установка, false - восстановление
     */
    private static void installRestoreError(String cause, Exception err, boolean isInstall) {
        if (isInstall) {
            System.err.println("Unable to continue installation: " + cause + ": " + err);
            System.exit(EXIT_INSTALL_ERROR);
        } else {
            System.err.println("Unable to continue recovery: " + cause + ": " + err);
            System.exit(EXIT_RESTORE_ERROR);
        }
    }

    /**
     * Ошибка установки/восстановления. С указанием причины
     *
     * @param cause     Причина отказа
     * @param isInstall true - установка, false - восстановление
     */
    private static void installRestoreError(String cause, boolean isInstall) {
        if (isInstall) {
            System.err.println("Unable to continue installation: " + cause);
            System.exit(EXIT_INSTALL_ERROR);
        } else {
            System.err.println("Unable to continue recovery: " + cause);
            System.exit(EXIT_RESTORE_ERROR);
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
