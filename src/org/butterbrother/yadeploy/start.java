package org.butterbrother.yadeploy;

/**
 * Запуск начинается здесь
 */
public class start
        implements staticValues {
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
                for (String line : helpList)
                    System.out.println(line);
                System.exit(EXIT_NORMAL);
                break;
            case WORK_MODE_BACKUP:
                // Создаём бекап текущего приложения
                targetedAction.doBackup(settings, ticket.getBackupOnlyAllow(settings));
                System.out.println("Backup done");
                System.exit(EXIT_NORMAL);
                break;
            case WORK_MODE_DEPLOY:
                // Разворачиваем деплой нового приложения
                ticket installTicket = ticket.getInstallAllow(settings);
                // Предварительный бекап текущего приложения
                try {
                    targetedAction.doBackup(settings, ticket.getBackupBeforeAllow(settings));
                } catch (ActionNotAvailable info) {
                    System.err.println("Backup not available, skip.");
                }
                // Установка
                targetedAction.doDeploy(settings, installTicket, true);
        }
    }
}
