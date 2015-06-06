package org.butterbrother.yadeploy;

/**
 * Статичные значения, константы.
 * Общие для всего приложения
 */
public interface staticValues {
    // Версия приложения
    String yaDeployVersion = "0.0.1a";
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
    int EXIT_NORMAL = 0;        // Нормальное завершение
    int EXIT_CONFIG_ERROR = 1;  // Ошибка чтения и обработки файла конфигурации
    int EXIT_BACKUP_CHECK_ERROR = 2;    // Ошибка при проверке на возможность бекапа
    int EXIT_BACKUP_ERROR = 3;  // Ошибка выполнения бекапа

    /*
     * Настройки в ini-файле
     */
    // [main]
    String MAIN_SECTION = "main";
    // releases = каталог с релизами, обязательный параметр
    String RELEASES_PATH = "releases";
    // backups = каталог с бекапами - обязательный
    String BACKUPS_PATH = "backups";
    // temp = каталог с временными файлами - не обязательный. Если не указывается, создаём в системном TEMP
    String TMP_PATH = "temp";
    // deploy = каталог с активным приложением - обязательный
    String DEPLOY_PATH = "deploy";

    /*
     * Строка справки
     */
    String[] helpList = {
            "yaDeploy, v. " + yaDeployVersion,
            "Small utility to deploy, update and backup servlets",
            "Usage:",
            "java -jar yadeploy.jar [--help] [--debug] [--config file_name] work_mode [release/backup_name]",
            "Switches:",
            "-h\t|\t--help\t\tShow this help",
            "-d\t|\t--debug\t\tVerbose output",
            "-c\t|\t--config\tfile_name\tUse another config file",
            "Work modes:",
            "b\t|\tbackup\t\tBackup current release",
            "r\t|\trestore\t\tRestore previous release, if backed up",
            "i\t|\tinstall\t\tUpdate (if exists) or install release",
            "Release:",
            "Numbers (if set regexp search mask), postfix (if backup or restore) or full file name.",
            "",
            "Config file - windows ini-file. Encoding - UTF-8. End line - no care.",
            "Default file name is \"yadeploy.ini\", place as folder same of application jar-file."
    };
}
