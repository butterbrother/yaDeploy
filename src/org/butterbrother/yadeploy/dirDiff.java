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
 * Выполняет сравнение перечисленных в параметре файлов для сравнения.
 * Предлагает замену/дополнение файла, показывает различия тестовых файлов.
 */
public class dirDiff {
    private String[] installFilesList;  // Список отслеживаемых файлов из новой установки
    private String[] deployFileList;    // Список отслеживаемых файлов из деплоя
    private String extractedPath;       // Полный путь к распакованному каталогу установки
    private String deployPath;          // Полный путь к каталогу деплоя

    /**
     * Инициализация
     *
     * @param extracted  Каталог с распакованным релизом
     * @param deploy     Каталог с деплоем
     * @param watchList  Список файлов для сравнения
     * @param ignoreList Список игнорируемых файлов. Желательно скомбинировать со списком удаляемых
     */
    public dirDiff(Path extracted, Path deploy, String watchList[], String ignoreList[]) {
        this.extractedPath = extracted.toString();
        this.deployPath = deploy.toString();
        this.installFilesList = getFilesList(extracted.toFile(), watchList, ignoreList);
        this.deployFileList = getFilesList(deploy.toFile(), watchList, ignoreList);
    }

    /**
     * Сканирует выбранный каталог. Составляет суммарный список отслеживаемых файлов
     *
     * @param directory  Сканируемый каталог
     * @param watchList  Список отслеживаемых файлов
     * @param ignoreList Список игнорируемых файлов
     * @return Полный список отслеживаемых файлов. Файлы будут иметь относительные
     */
    private String[] getFilesList(File directory, String[] watchList, String[] ignoreList) {
        // Сканируем каталог
        DirectoryScanner dirList = new DirectoryScanner();
        dirList.setBasedir(directory);
        dirList.setIncludes(watchList);
        dirList.setExcludes(ignoreList);
        dirList.setCaseSensitive(false);
        dirList.scan();
        // Отдаём список файлов для сравнения
        return dirList.getIncludedFiles();
    }

    /**
     * Выполняет сравнение файлов в исходном каталоге и каталоге деплоя
     * Предоставляет выбор пользователю о необходимости обновить файл, либо оставить оригинальный
     *
     * @throws IOException  Ошибка при выполнении сравнения
     */
    public void doRetursiveDiff() throws IOException {
        diffBothAvailableFiles();
        applyNewFiles();
        saveOldFiles();
    }

    /**
     * Вначале обрабатывает файлы, которые присутствуют в обоих каталогах
     *
     * @throws IOException  Ошибка при рекурсивной обработке
     */
    private void diffBothAvailableFiles() throws IOException {
        for (String install : installFilesList) {
            for (String original : deployFileList) {
                if (install.equalsIgnoreCase(original)) {
                    Path newFile = Paths.get(extractedPath, install); // Новый файл из установки
                    Path deployFile = Paths.get(deployPath, original);// Текущий файл из деплоя
                    // Сравниваем хэши файлов
                    if (! compareMD5digests(newFile, deployFile)) {
                        // Предлагаем выбрать между старым и новым файлом
                        // Для текстовых файлов отображаем версию выбора с diff
                        if (isTextFile(install)) {
                            if (!needChangeTextFile(newFile, deployFile))  // Если менять файл не нужно - копируем его из деплоя
                                Files.copy(deployFile, newFile);
                        } else {
                            // Для бинарных - только спрашиваем
                            if (!needChangeBinaryFile(install))
                                Files.copy(deployFile, newFile);
                        }
                    }
                }
            }
        }
    }

    /**
     * Определение, является ли файл текстовым
     *
     * Определение производится по расширению файла
     *
     * @param fileName  имя файла
     * @return          true - текстовый, false - бинарный
     */
    private boolean isTextFile(String fileName) {
        String textFileTypes[] = { ".txt", ".xml", ".properties", ".sql", ".conf" };

        for (String type : textFileTypes)
            if (fileName.toLowerCase().endsWith(type))
                return true;

        return false;
    }

    /**
     * Обработка новых файлов, которые отсутствуют в списке отслеживаемых в деплое
     *
     * @throws IOException  Ошибка при рекурсивной обработке
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
                // Определяем необходимость в новом файле
                Path newFile = Paths.get(extractedPath, install); // Новый файл из установки
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
     * Обработка старых файлов, которых нет в новом релизе
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
                // Определяем необходимость сохранения старого файла
                Path oldFile = Paths.get(deployPath, original); // Текущий файл деплоя
                Path savedFile = Paths.get(extractedPath, original); // Сохраняемый файл
                Path parrent = savedFile.getParent();   // Родительский каталог сохраняемого файла, для воссоздания структуры
                if (isTextFile(original)) {
                    if (needAddOrSaveTextFile(oldFile, false)) {
                        // Воссоздаём структуру каталогов и копируем
                        Files.createDirectories(parrent);
                        // Копируем из деплоя в новую установку
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
     * Необходимость добавления нового либо удаления старого текстового файла
     *
     * @param changeFile    заменяемый файл
     * @param isNewFile     true - новый файл
     * @return              необходимость замены
     * @throws IOException  Ошибка чтения текстового файла
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
     * Необходимость добавления нового либо удаления старого бинарного файла
     *
     * @param fileName  заменяемый файл
     * @param isNewFile true - новый файл
     * @return          необходимость замены
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
     * Необходимость обновления бинарного файла
     *
     * @param fileName  Имя файла
     * @return          Необходимость обновления
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
     * Необходимость обновления текстового файла
     *
     * @param newFile       Новый файл
     * @param deployFile    Файл из деплоя
     * @return              Необходимость замены. true - оставляем новый файл. false - копируем файл из
     * деплоя в распакованный каталог
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
     * Отображает разницу в виде diff-а между двумя текстовыми файлами
     *
     * @param newFile       Новый файл
     * @param deployFile    Текущий файл
     * @throws IOException  Ошибка чтения одного из файлов
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
     * Считывает из файла в связанный список
     *
     * @param file  Файл
     * @return      Связанный список
     * @throws IOException  Ошибка чтения файла
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
     * Сравнивает файлы по MD5-хешу
     *
     * @param one Первый файл
     * @param two Второй файл
     * @return Совпадает либо нет
     * @throws IOException Ошибка ввода-вывода при выполнении чтения файла, при сравнении
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
     * Рассчёт MD5-хеша файла
     *
     * @param file Входной файл
     * @return MD5-хеш
     * @throws IOException Ошибка ввода-вывода при чтении файла
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
