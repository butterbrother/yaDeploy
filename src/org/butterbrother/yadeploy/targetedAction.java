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
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
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
                System.out.println();
                try (Progress dirCreatingProgress = new Progress("Creating ZIP directory structure:", dirs.length)) {
                    for (String dir : dirs) {
                        // Отображаем бегунок
                        dirCreatingProgress.inc();
                        if (dir.isEmpty()) continue;    // Пропускаем добавление корневого каталога - имя архива
                        ZipEntry zipped = new ZipEntry(dir + "/");
                        // Получаю дату-время модификации
                        FileTime modTime = Files.getLastModifiedTime(Paths.get(direction.getSourceFullName(), dir));
                        zipped.setTime(modTime.toMillis());
                        zipFile.putNextEntry(zipped);
                        zipFile.closeEntry();
                    }
                }

                // Сжимаем файлы
                try (Progress filesCompressProgress = new Progress("Compressing files:", files.length)) {
                    for (String file : files) {
                        // Отображение бегунка
                        filesCompressProgress.inc();
                        ZipEntry zipped = new ZipEntry(file);
                        // Получаю дату-время модификации файла
                        FileTime modTime = Files.getLastModifiedTime(Paths.get(direction.getSourceFullName(), file));
                        zipped.setTime(modTime.toMillis());
                        zipFile.putNextEntry(zipped);
                        try (BufferedInputStream inpDir = new BufferedInputStream(Files.newInputStream(Paths.get(direction.getSourceFullName(), file)), 4096)) {
                            while ((length = inpDir.read(buffer)) > 0)
                                zipFile.write(buffer, 0, length);
                        } catch (IOException inpDirReadErr) {
                            filesCompressProgress.addError("Unable to compress file " + file + ": " + inpDirReadErr);
                        }
                        zipFile.closeEntry();
                    }
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
            try (Progress createDirsProgress = new Progress("Creating same directory structure", dirs.length)) {
                for (String dir : dirs) {
                    // Отображаем бегунок
                    createDirsProgress.inc();

                    // Создаём каталог
                    try {
                        Files.createDirectories(Paths.get(backupDir.toString(), dir));
                    } catch (IOException ioErr) {
                        createDirsProgress.addError("Unable to create dir " + dir + ": " + ioErr);
                    }
                }
            }

            // Последовательно копируем файлы
            try (Progress copyFilesProgress = new Progress("Backup files:", files.length)) {
                for (String file : files) {
                    // Отображаем бегунок
                    copyFilesProgress.inc();
                    try {
                        Files.copy(
                                Paths.get(direction.getSourceFullName(), file),
                                Paths.get(backupDir.toString(), file),
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.COPY_ATTRIBUTES
                        );
                    } catch (IOException ioErr) {
                        copyFilesProgress.addError("Unable to backup " + file + ": " + ioErr);
                    }
                }
            }
        }
    }

    /**
     * Разворачивает релиз/бекап из архива в деплой
     * Статичная лапша, но и так сойдёт
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

        // Удаляем файлы из спаска для удаления, из распакованного релиза
        try {
            deleteExcluded(currentTempDir, deleteList);
        } catch (IOException err) {
            installRestoreError("unable to delete files/directories from delete list", err, isInstall);
        }

        // Выполняем сравнение, если каталог с деплоем существует
        try {
            if (Files.exists(direction.getDestinationPath())) {
                dirDiff differ = new dirDiff(currentTempDir, direction.getDestinationPath(), getWatchList(settings), ignoreList);
                differ.doRetursiveDiff();
            }
        } catch (IOException err) {
            installRestoreError("error check difference", err, isInstall);
        }

        // Предварительный бекап текущего приложения
        try {
            targetedAction.doBackup(settings, ticket.getBackupBeforeAllow(settings));
        } catch (ActionNotAvailable info) {
            System.err.println("Backup not available, skip.");
        }

        // Если не существует - создаём
        try {
            if (Files.notExists(direction.getDestinationPath())) {
                Files.createDirectories(direction.getDestinationPath());
            }
        } catch (IOException err) {
            installRestoreError("unable to create deploy path", err, isInstall);
        }

        // Очищаем деплой
        cleanupDeploy(direction.getDestinationPath(), ignoreList, isInstall);

        // Копируем из временного каталога в деплой
        try {
            recursiveCopyDir(currentTempDir, direction.getDestinationPath());
        } catch (IOException err) {
            installRestoreError("unable copy files to deploy", err, isInstall);
        }

        // Удаляем временный каталог
        try {
            FileUtils.deleteDirectory(currentTempDir.toFile());
        } catch (IOException err) {
            System.err.println("Warn: unable to delete temporary path " + currentTempDir.toString());
        }

        // Если это системный временный каталог - удаляем и его
        if (settings.temporaryInSystemTemp())
            try {
                FileUtils.deleteDirectory(direction.getTemporariesFile());
            } catch (IOException err) {
                System.err.println("Warn: unable to delete application temporary path in system temp");
            }

        // Отображаем статистику
        System.out.println("Archive statistic:");
        archiveStat.showStatistic();
        System.out.println("Deploy statistic:");
        deployStat.showStatistic();
    }

    /**
     * Выполняет очистку каталога деплоя, игнорируя файлы и каталоги из игнорируемого списка
     *
     * @param deployPath Каталог деплоя
     * @param ignoreList Игнорируемый список
     */
    private static void cleanupDeploy(Path deployPath, String ignoreList[], boolean isInstall) {
        DirectoryScanner cleanList = new DirectoryScanner();
        cleanList.setBasedir(deployPath.toFile());
        cleanList.setExcludes(ignoreList);
        cleanList.scan();

        try (Progress cleanupProgress = new Progress(
                "Cleanup deploy directory " + deployPath.toString(),
                cleanList.getIncludedDirsCount() + cleanList.getIncludedFilesCount())) {
            try {
                // Удаляем файлы
                for (String file : cleanList.getIncludedFiles()) {
                    cleanupProgress.inc();
                    Files.delete(Paths.get(deployPath.toString(), file));
                }
                // Удаляем каталоги
                for (String dir : cleanList.getIncludedDirectories()) {
                    cleanupProgress.inc();
                    if (dir.isEmpty()) continue;    // Пропускаем сам каталог деплоя
                    FileUtils.deleteDirectory(Paths.get(deployPath.toString(), dir).toFile());
                }
            } catch (IOException err) {
                cleanupProgress.close();
                installRestoreError("unable to cleanup deploy", err, isInstall);
            }
        }
    }

    /**
     * Получение списка наблюдаемых файлов
     *
     * @param settings Файл настроек
     * @return Список наблюдаемых файлов. Если их нет - массив будет с 0 элементов
     */
    private static String[] getWatchList(configStorage settings) {
        try {
            return settings.getSepatatedParameter(DEPLOY_SECTION, DEPLOY_WATCH);
        } catch (ParameterNotFoundException exp) {
            System.out.println("Watchable list not set and will not be used");
            return new String[0];
        }
    }

    /**
     * Получение списка игнорируемых файлов из конечного деплоя
     *
     * @param settings Файл настроек
     * @return Список игнорируемых файлов. Если их нет - массив будет с 0 элементов.
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
     * @param settings Файл настроек
     * @return Список игнорируемых файлов. Если их нет - массив будет с 0 элементов.
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
     * @param settings  Настройки из файла настроек
     * @param direction Файлы источника и получателя, разрешение
     * @param isInstall true - установка, false - восстановление
     * @return Файл/каталог с релизом либо бекапом
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
                String filter[] = {direction.getDestinationPath().getFileName().toString() + "*"};
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
                        if (!validateFileType(releaseFile)) {
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
                        System.out.println(++index + " " + item);
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
                if (!validateFileType(installPath))
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
     * Удаление из временного каталога с распакованными файлами файлов из списка для удаления
     *
     * @param extractedPath Каталог распакованных файлов
     * @param deleteList    Список файлов для удаления
     * @throws IOException Ошибка ввода-вывода при выполнении какого-либо действия
     */
    private static void deleteExcluded(Path extractedPath, String[] deleteList) throws IOException {
        DirectoryScanner forDel = new DirectoryScanner();
        forDel.setBasedir(extractedPath.toFile());
        forDel.setIncludes(deleteList);
        forDel.scan();
        Formatter progressBar = new Formatter(System.out);
        if ((forDel.getIncludedFilesCount() + forDel.getIncludedDirsCount()) > 0) {
            progressBar.format("Delete files from delete list:\n");
            for (String item : forDel.getIncludedFiles()) {
                progressBar.format("-- Delete file: %s\n", item);
                Path removeFile = Paths.get(extractedPath.toString(), item);
                Files.delete(removeFile);
            }
            for (String item : forDel.getIncludedDirectories()) {
                progressBar.format("-- Delete directory: %s\n", item);
                Path removeDir = Paths.get(extractedPath.toString(), item);
                FileUtils.deleteDirectory(removeDir.toFile());
            }
            progressBar.format("Delete files from list complete\n");
        }
    }

    /**
     * Проверка верного типа файла, годного для деплоя.
     * Приложение поддерживает только zip-архивы:
     * .zip и .war
     * Не проверяются - распакованные каталоги.
     *
     * @param releaseFile Проверяемый файл
     * @return Поддерживаемость файла приложением
     */
    private static boolean validateFileType(Path releaseFile) {
        if (Files.isDirectory(releaseFile)) return true;
        String fileStringPath = releaseFile.toString().toLowerCase();
        return (fileStringPath.endsWith(".zip") || (fileStringPath.endsWith(".war")));
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
        try (Progress copyProgress = new Progress(
                "Copy directory recursively:",
                copyFiles.getIncludedDirsCount() + copyFiles.getIncludedFilesCount())
        ) {
            // Воссоздаём структуру
            for (String item : copyFiles.getIncludedDirectories()) {
                copyProgress.inc();
                if (item.isEmpty()) continue;
                Files.createDirectories(Paths.get(destination.toString(), item));
            }
            // Копируем файлы
            for (String item : copyFiles.getIncludedFiles()) {
                copyProgress.inc();
                Path sourceFile = Paths.get(source.toString(), item);
                Path targetFile = Paths.get(destination.toString(), item);
                Files.copy(sourceFile, targetFile);
            }
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
        // Определяем кодировку
        Charset zipEncoding = fileChangeStatistic.detectZipEncoding(zipFile);
        // Предварительно получаем число элементов архива
        int zipEntriesCount;
        try (ZipFile cnt = new ZipFile(zipFile.toFile(), zipEncoding)) {
            zipEntriesCount = cnt.size() > 0 ? cnt.size() : 10000;
        }

        // Распаковываем
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile), 4096), zipEncoding)) {
            int length;
            byte buffer[] = new byte[4096];
            ZipEntry archive;
            try (Progress extractProgress = new Progress("Extract archive:", zipEntriesCount)) {
                while ((archive = zip.getNextEntry()) != null) {
                    extractProgress.inc();
                    if (archive.isDirectory()) {
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
