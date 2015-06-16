package org.butterbrother.yadeploy;

import org.apache.tools.ant.DirectoryScanner;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Обсчёт статстики по файлам
 */
public class fileChangeStatistic {
    // Самый старый файл, имеет меньшее значение в мс., поэтому для сравнения с первым
    // файлом число в мс. должно иметь максимально мозможное значение
    private long oldestFile = Long.MAX_VALUE;
    // Самый новый файл. В противоположность - имеет наибольшее значение. Поэтому для
    // сравнения с датой первого файла должен быть с минимально возможным значением
    private long newestFile = 0;
    // Самая распространённая дата модификации файлов, дата-число файлов
    private TreeMap<Long, Integer> mostModTime = new TreeMap<>();
    // Дата модификации вышестоящего каталога либо архива
    private long parentModTime = 0;

    public fileChangeStatistic() {
    }

    /**
     * Добавление файла в статистику
     *
     * @param fileModTime Дата модификации в мс.
     */
    public void addFile(long fileModTime) {
        if (fileModTime <= 0) return;   // Исключаем файлы, для которых не удалось получить дату модификации

        // Ищем в дереве дат
        boolean found = false;
        for (Map.Entry<Long, Integer> item : mostModTime.entrySet()) {
            if (item.getKey() == fileModTime) {
                // Если находим - прибавляем в статистике
                item.setValue(item.getValue() + 1);
                found = true;
            }
        }
        if (!found)
            mostModTime.put(fileModTime, 1);

        // Сравниваем с текущими датами
        if (fileModTime > newestFile)
            newestFile = fileModTime;
        if (fileModTime < oldestFile)
            oldestFile = fileModTime;
    }

    /**
     * Добавление файла в статистику
     *
     * @param file Файл
     * @throws IOException Ошибка при получении даты-времени модификации
     */
    public void addFile(Path file) throws IOException {
        addFile(Files.getLastModifiedTime(file).toMillis());
    }

    /**
     * Устанавливает дату и время модификации вышестоящего каталога либо архива.
     *
     * @param fileModTime Дата модификации в мс.
     */
    public void setParentModTime(long fileModTime) {
        parentModTime = fileModTime;
    }

    /**
     * Обсчитывает статистику для каталога целиком, ключая сам каталог,
     * подкаталоги и подфайлы. Метод не обнуляет текущую статистику
     *
     * @param file    Файл
     * @param ignored Список исключений
     * @param deleted Список удаляемых файлов
     * @throws IOException Ошибка ввода-вывода при рассчёте
     */
    public void calculatePath(Path file, String[] ignored, String[] deleted) throws IOException {
        // Суммарный массив для рассчёта статистики
        LinkedList<String> comboIgnoreList = new LinkedList<>();
        if (ignored != null && ignored.length > 0)
            comboIgnoreList.addAll(Arrays.asList(ignored));
        if (deleted != null && deleted.length > 0)
            comboIgnoreList.addAll(Arrays.asList(deleted));

        String comboIgnore[] = comboIgnoreList.toArray(new String[comboIgnoreList.size()]);

        // Обрабатываем статистику в каталоге
        if (Files.exists(file)) {
            // Для каталога отдельно
            setParentModTime(file);
            // И отдельно для...
            DirectoryScanner deployList = new DirectoryScanner();
            deployList.setBasedir(file.toString());
            if (comboIgnore.length > 0)
                deployList.setExcludes(comboIgnore);
            deployList.scan();
            // ...Каталогов
            for (String item : deployList.getIncludedDirectories()) {
                if (item.isEmpty()) continue; // Пропускаем корневой каталог
                Path includedDir = Paths.get(file.toString(), item);
                addFile(includedDir);
            }
            // ...и файлов
            for (String item : deployList.getIncludedFiles()) {
                Path includedFile = Paths.get(file.toString(), item);
                addFile(includedFile);
            }
        }
    }

    /**
     * Обсчитывает статистику для zip-архива целиком, включая сам zip-архив
     * Метод не обнуляет текущую статистику.
     *
     * @param zipFile zip-архив
     * @throws IOException Ошибка ввода-вывода при рассчёте
     */
    public void calculateZip(Path zipFile) throws IOException {
        setParentModTime(zipFile);

        Charset zipEncoding = detectZipEncoding(zipFile);
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream((Files.newInputStream(zipFile)), 4096), zipEncoding)) {
            ZipEntry archive;
            while ((archive = zip.getNextEntry()) != null) {
                // Обрабатываем статистические данные
                addFile(archive.getTime());
                // Прокручиваем архив далее
                zip.closeEntry();
            }
        }
    }

    /**
     * Определение кодировки zip-архива.
     *
     * Выполняет банальный перебор по всем возможным кодировкам.
     * Возвращает ту, с помощью которой удалось перечислить файлы в архиве.
     *
     * @param zipFile       zip-архив
     * @return              Кодировка, подходящая для открытия
     * @throws IOException  Если ничего не нашлось/ошибка открытия файла
     */
    public static Charset detectZipEncoding(Path zipFile) throws IOException {
        // Некорректные для имени файлов символы
        String incorrectCharacters[] = { "\"", "*", ":", "<", ">", "?", "|" };
        // Получаем все возможные кодировки
        LinkedHashMap<String, Charset> allCharsets = new LinkedHashMap<>();
        // Вставляем в начало самые вероятные - это киррилица DOS, текущая и кириллица Windows
        allCharsets.put("ru-dos", Charset.forName("CP866"));
        allCharsets.put("current-system", Charset.defaultCharset());
        allCharsets.put("ru-win", Charset.forName("WINDOWS-1251"));
        allCharsets.put("utf8_ru", Charset.forName("UTF-8"));
        // Далее по списку вставляем вообще все доступные комбинации
        allCharsets.putAll(Charset.availableCharsets());
        // А теперь пробуем перечислить все файлы с данными кодировками
        for (Map.Entry<String, Charset> probe : allCharsets.entrySet()) {
            encodeTry:
            {
                try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile), 4096), probe.getValue())) {
                    // Прокручиваем архив
                    ZipEntry compressed;
                    while ((compressed = zip.getNextEntry()) != null) {
                        for (String item : incorrectCharacters) {
                            if (compressed.getName().contains(item)) {
                                // Если имя файла содержит неверные символы - сбрасываем
                                zip.closeEntry();
                                break encodeTry;
                            }
                        }
                        zip.closeEntry();
                    }
                } catch (IllegalArgumentException ignore) {
                    // Все исключения мужественно игнорируем. Почти все
                    continue;
                }
                return probe.getValue();
            }
        }

        // Если ничего не нашли
        throw new IOException("Unable detect ZIP file encoding");
    }

    /**
     * Возвращает дату модификации вышестоящего каталога либо архива
     *
     * @return Дата модификации
     */
    public Calendar getParentModTime() {
        Calendar modTime = Calendar.getInstance();
        modTime.setTimeInMillis(parentModTime);
        return modTime;
    }

    /**
     * Устанавливает дату и время модификации вышестоящего каталога либо архива.
     *
     * @param file Каталог либо файл
     * @throws IOException Ошибка при получении даты-времени модификации
     */
    public void setParentModTime(Path file) throws IOException {
        setParentModTime(Files.getLastModifiedTime(file).toMillis());
    }
}
