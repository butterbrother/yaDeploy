package org.butterbrother.selectFSItem;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Фильтрация списка
 */
public class dirStreamFilter<T extends Path>
        implements DirectoryStream.Filter<T> {
    private boolean dirOnly;
    private String filter;

    /**
     * Инициализация фильтра с фильтрацией по
     * имени файла/расширения
     *
     * @param filter  Фильтр
     * @param dirOnly Флаг - только каталоги,
     *                параметр фильтра будет игнорироваться
     */
    public dirStreamFilter(String filter, boolean dirOnly) {
        this.dirOnly = dirOnly;
        this.filter = filter;
    }

    /**
     * Сама фильтрация
     *
     * @param entry Элемент - файл либо каталог
     * @return Пропуск фильтра
     * @throws IOException
     */
    @Override
    public boolean accept(T entry) throws IOException {
        // Всегда пропускаем каталоги
        if (Files.isDirectory(entry)) return true;
        // Режим - только каталоги, не пропускаем файлы
        if (dirOnly && !Files.isDirectory(entry)) return false;
        // Если фильтр - все файлы, то пропускаем все
        if (filter.equals("*")) return true;

        // Получаем имя файла и проверяем по маске
        return Pattern.compile(filter, Pattern.CASE_INSENSITIVE).matcher(entry.getFileName().toString()).matches();
    }
}
