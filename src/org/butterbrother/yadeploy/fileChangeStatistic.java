package org.butterbrother.yadeploy;

import org.apache.tools.ant.DirectoryScanner;

import java.io.BufferedInputStream;
import java.io.IOException;
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

        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile), 4096))) {
            ZipEntry archive;
            while ((archive = zip.getNextEntry()) != null) {
                // Обрабатываем статистические данные
                addFile(archive.getTime());
            }
        }
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
