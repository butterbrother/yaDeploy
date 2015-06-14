package org.butterbrother.selectFSItem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Различные файловые диалоги
 */
public class dialogs {
    /**
     * Запрос на создание каталога либо выбор существующего
     *
     * @param location Расположение (из файл конфигурации, например)
     * @return Новое расположение. Может быть null
     * @throws IOException Ошибка при выборе/создании каталога
     */
    public static Path answerCreateSelectPath(String prompt, String location) throws IOException {
        while (true) {
            System.out.println(prompt);
            System.out.println("Would you like create path " + location + "?");
            System.out.println("1) yes");
            System.out.println("2) no");
            System.out.println("3) select another directory");
            System.out.print(">> ");
            int menuItem;
            try {
                menuItem = Integer.parseInt(System.console().readLine());
            } catch (NumberFormatException err) {
                System.out.println("Can't parse, try again");
                continue;
            }
            switch (menuItem) {
                case (1):
                    return Files.createDirectory(Paths.get(location));
                case (2):
                    return null;
                case (3):
                    return selectItem.selectFile("./", "*", true);
            }
        }
    }
}
