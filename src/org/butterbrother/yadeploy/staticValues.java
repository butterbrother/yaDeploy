package org.butterbrother.yadeploy;

/**
 * Статичные значения, константы.
 * Общие для всего приложения
 */
public interface staticValues {
    // Версия приложения
    String yaDeployVersion = "0.2.0b";
    /*
     * Режимы работы
     */
    int WORK_MODE_NOTHING = 0;  // Ничего не делаем
    int WORK_MODE_BACKUP = 1;   // Делаем бекап
    int WORK_MODE_RESTORE = 2;  // Восстанавливаем из бекапа
    int WORK_MODE_DEPLOY = 3;   // Разворачиваем приложение
    int WORK_MODE_HELP = 4;     // Отображаем справку

    /*
     * Коды возврата
     */
    int EXIT_NORMAL = 0;                // Нормальное завершение
    int EXIT_CONFIG_ERROR = 1;          // Ошибка чтения и обработки файла конфигурации
    int EXIT_GENERAL_ERROR = 2;         // Общая ошибка
    int EXIT_BACKUP_CHECK_ERROR = 3;    // Ошибка при проверке на возможность бекапа
    int EXIT_BACKUP_ERROR = 4;          // Ошибка выполнения бекапа
    int EXIT_INSTALL_CHECK_ERROR = 5;   // Ошибка при проверке на возможность установки
    int EXIT_INSTALL_ERROR = 6;         // Ошибка выполнения установки
    int EXIT_RESTORE_CHECK_ERROR = 7;   // Ошибка при проверке на возможность восстановления
    int EXIT_RESTORE_ERROR = 8;         // Ошибка при восстановлении


    /*
     * Настройки в ini-файле
     */
    // [main]
    String MAIN_SECTION = "main";
    // temp = каталог с временными файлами - не обязательный. Если не указывается, создаём в системном TEMP
    String TMP_PATH = "temp";

    // [backup]
    String BACKUPS_SECTION = "backup";
    // Настройки бекапа
    // path = путь к каталогу бекапов
    String BACKUPS_LOCATION = "path";
    // ignore = игнорируемые пути и файлы
    String BACKUPS_IGNORE = "ignore";
    // type = тип архивации, zip - архив. Иное - каталог
    String BACKUPS_TYPE = "type";

    // [deploy]
    String DEPLOY_SECTION = "deploy";
    // Настройки развёртывания и восстановления
    // path = путь к каталогу деплоя
    String DEPLOY_LOCATION = "path";
    // watch = эти файлы проверяются на наличие изменений, разделение - точка с запятой
    String DEPLOY_WATCH = "watch";
    // ignore = эти файлы и каталоги игнорируются при обновлении и восстановлении
    String DEPLOY_IGNORE = "ignore";
    // delete = эти файлы удаляются при развёртывании
    String DEPLOY_DELETE = "delete";

    // [releases]
    String RELEASES_SECTION = "releases";
    // Настройки архивов приложений
    // path = путь к каталогу с приложениями
    String RELEASES_LOCATION = "path";
    // filter = фильтрация по регекспу (ant regexp)
    String RELEASES_REGEXP = "filter";

    /*
     * Строка справки
     */
    String[] helpList = {
            "yaDeploy, v. " + yaDeployVersion,
            "Small utility to deploy, update and backup servlets",
            "",
            "Usage:",
            "java -jar yadeploy.jar [--help] [--debug] [--config file_name] work_mode [release/backup_name]",
            "",
            "Switches:",
            "-h | --help\t\t\tShow this help",
            "-d | --debug\t\t\tVerbose output",
            "-c | --config\tfile_name\tUse another config file",
            "",
            "Work modes:",
            "b | backup\t\t\tBackup current release",
            "r | restore\t\t\tRestore previous release, if backed up",
            "i | install\t\t\tUpdate (if exists) or install release",
            "",
            "Release:",
            "Numbers (if set regexp search mask), postfix (if backup or restore) or full file name.",
            "",
            "Config file - windows ini-file. Encoding - UTF-8. End line - no care.",
            "Default file name is \"yadeploy.ini\", place as folder same of application jar-file.",
            "",
            "Config structure:",
            "[main]",
            "temp = path to temporary directory. If not set - will be used system temporary",
            "       directory.",
            "",
            "[backup]",
            "path = path to backups directory. Must be specified.",
            "ignore = relative path to files/directories/masks which will be ignored during",
            "         backup. Separated by semicolon",
            "type = backup type. If zip (default) backup will be compressed to zip-archive.",
            "       Another param - plain directory.",
            "",
            "[deploy]",
            "path = path to work deploy directory. Must be specified.",
            "watch = relative path fo files/directories/mask which will be checked for",
            "        changes. The system will offer a choice - to use the new, or keep the",
            "        old file. Separated by semicolon",
            "ignore = relative path to files/directories/masks which will be ignored during",
            "         install or restore. Separated by semicolon",
            "delete = relative path to files/directories/masks which will be deleted during",
            "         install or restore.",
            "",
            "[releases]",
            "path = path to install archives or extracted directories. Must be specified.",
            "       System support only extracted directories or zip (war) files",
            "filter = mask is used during installation and recovery. This is the first",
            "         filter, the second - indicates the release (if specified on the",
            "         command line argument, or the filter is ignored)"
    };
}
