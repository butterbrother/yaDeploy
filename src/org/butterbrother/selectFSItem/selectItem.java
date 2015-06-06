package org.butterbrother.selectFSItem;

import java.io.Console;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Осуществляет навигацию в консоли, выборку файлов
 */
public class selectItem {

    /**
     * Выбор объекта (файловая навигация)
     *
     * @param startPosition Начальная позиция
     * @param mask          Маска файла, согласно Java Regexp
     * @param dirOnly       Отображать только каталоги
     * @return Выбор пользователя либо null, если пользователь отказался
     * @throws IOException Сбой выбора файла
     */
    public static Path selectFile(String startPosition, String mask, boolean dirOnly) throws IOException {
        // Создаём объект начальной позиции обзора файлов
        Path navPath = Paths.get(startPosition);
        // Вылетаем с исключением, если такого нет
        if (Files.notExists(navPath))
            throw new IOException("File not exists");

        navPath = navPath.toAbsolutePath();
        // Если указан файл - получаем его вышестоящую директорию
        if (!Files.isDirectory(navPath))
            navPath = navPath.getParent();

        Formatter listEl = new Formatter(System.out); // Вспомогательный форматтер
        Date fileDate = new Date();           // И дата для отображения даты файлов
        Console input = System.console();       // Пользовательский ввод
        // Отсюда начинаем навигацию
        do {
            listEl.format("\n< %s >\n", navPath.toAbsolutePath().toString()); // Отображаем текущий каталог
            // Приглашение
            if (dirOnly) {
                listEl.format("Select directory:\n");
            } else {
                listEl.format("Select file or directory:\n");
            }

            // Список элементов в каталоге
            LinkedHashMap<Integer, Path> itemList = new LinkedHashMap<>();
            int count = 0;  // Для перечисления элементов
            try (DirectoryStream<Path> list = Files.newDirectoryStream(navPath, new dirStreamFilter<>(mask, dirOnly))) {
                // Заполняем список из текущего каталога
                for (Path item : list)
                    itemList.put(++count, item);
            } catch (DirectoryIteratorException ignore) {
            } catch (AccessDeniedException ignore) {
                listEl.format("<Access denied, unable to enumerate files list>\n");
                listEl.format("<You can go to parent directory or enter path manually>\n");
            }


            // Отображаем список с перечислениями элементов
            listEl.format("%3s  %-30s  %5s  %16s  %16s \n", "NUM", "File Name", "Attr", "Created", "Modified");
            if (itemList.size() == 0)
                listEl.format("<Empty>\n");
            for (Map.Entry<Integer, Path> item : itemList.entrySet()) {
                try {
                    // Отображаем номер и имя файла
                    listEl.format("%3d  %-30s  ", item.getKey(), item.getValue().getFileName().toString());

                    // Считываем атрибуты
                    BasicFileAttributes attr = Files.readAttributes(item.getValue(), BasicFileAttributes.class);

                    // И по-очереди их выводим
                    // Тип файла
                    if (attr.isDirectory())
                        listEl.format("d");    // Каталог
                    else if (attr.isRegularFile())
                        listEl.format("-");    // Файл
                    else if (attr.isSymbolicLink())
                        listEl.format("l");    // Симлинк
                    else if (attr.isOther())
                        listEl.format("o");    // Прочее
                    else
                        listEl.format("u");    // Неизвестный тип

                    // Права
                    if (Files.isReadable(item.getValue())) {
                        listEl.format("r");
                    } else {
                        listEl.format("-");
                    }
                    if (Files.isWritable(item.getValue())) {
                        listEl.format("w");
                    } else {
                        listEl.format("-");
                    }
                    if (Files.isExecutable(item.getValue())) {
                        listEl.format("x");
                    } else {
                        listEl.format("-");
                    }
                    if (Files.isHidden(item.getValue())) {
                        listEl.format("h");
                    } else {
                        listEl.format("-");
                    }
                    listEl.format("  ");

                    // Дата создания
                    fileDate.setTime(attr.creationTime().toMillis());
                    listEl.format("%TY-%<Tm-%<Td %<TH:%<TM  ", fileDate);

                    // Дата модификации
                    fileDate.setTime(attr.lastModifiedTime().toMillis());
                    listEl.format("%TY-%<Tm-%<Td %<TH:%<TM  ", fileDate);

                    // Перенос строки в конце
                    listEl.format("\n");
                } catch (IOException attrErr) {
                    listEl.format("<Access denied>\n");
                }
            }

            // Далее предлагаем выбор элементов
            listEl.format("Enter number, \"o\" to select current directory, \"u\" or \"..\" switch to parent directory, \"m\" to manual enter, or \"q\" to exit >> ");
            String userInput = input.readLine();
            switch (userInput) {
                // Текущий каталог
                case "o":
                    return navPath;
                // Выход, вернём null
                case "q":
                    return null;
                // На уровень вверх
                case "u":
                case "..":
                    if ((navPath.getParent() == null)) {
                        listEl.format("Unable to switch parrent\n");
                    } else {
                        navPath = navPath.getParent();
                    }
                    break;
                // Указание пути вручную
                case "m":
                    listEl.format("Enter path >> ");
                    Path manualPath = Paths.get(input.readLine()).toAbsolutePath();
                    if (Files.notExists(manualPath)) {
                        listEl.format("File or path not exists\n");
                    } else {
                        return manualPath;
                    }
                    break;
                default:
                    try {
                        int numItem = Integer.parseInt(userInput);
                        // Получаем файл-путь из перечисления. Может быть null
                        navPath = itemList.get(numItem) != null ? itemList.get(numItem) : navPath;
                        // Если выбран файл - вернём в итоге файл
                        if (!Files.isDirectory(navPath))
                            return navPath;
                        // Иначе продолжим обзор
                    } catch (NumberFormatException | IndexOutOfBoundsException err) {
                        listEl.format("Nope, try again, bro\n");
                    }
            }
        } while (true);
    }
}
