package org.butterbrother.yadeploy;

/**
 * Запуск начинается здесь
 */
public class start
    implements staticValues{
    public static void main(String args[]) {
        // Считываем файл конфигурации и обрабатыватываем аргументы командной строки
        configStorage settings = configStorage.initialize(args);

        // Определяем своё дальнейшее поведение исходя из режима работы
        switch (settings.getWorkMode()) {
            case WORK_MODE_NOTHING:
                // Простой путь - ничего не делаем
                System.err.println("No work mode set. See --help");
                System.exit(EXIT_NORMAL);
                break;
            case WORK_MODE_HELP:
                // Отображаем справку
        }
    }
}
