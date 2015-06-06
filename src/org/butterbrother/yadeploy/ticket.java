package org.butterbrother.yadeploy;

import org.apache.tools.ant.DirectoryScanner;
import org.butterbrother.selectFSItem.dialogs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Предварительная проверка и последующее разрешение на выполнение действий.
 * <p/>
 * Так же хранит пути на необходимые для конкретного
 * режима работы каталоги, полученные в ходе проверки
 * <p/>
 * Непосредственные проверки на наличие путей выполняются хранилищем настроек.
 * Здесь проверяется допустимость работы при наличии/отсутствии каких-либо путей
 * <p/>
 * При провале проверки работа приложения завершится с характерной ошибкой
 */
public class ticket
    implements staticValues{
    private Path backupsPath;       // Каталог бекапов
    private Path releasesPath;      // Каталог с релизами
    private Path temporariesPath;   // Каталог временными файлами
    private Path deployPath;        // Каталог с текущим приложением

    private boolean deleteTemporary;// Удалить временный каталог после завершения действия

    /**
     * Внутренняя инициализация. Получить разрешение можно только пройдя
     * соответствующие проверки
     *
     * @param backupsPath     Каталог бекапов
     * @param releasesPath    Каталог релизов
     * @param temporariesPath Каталог временных файлов
     * @param deployPath      Каталог с развёрнутым приложенем
     * @param deleteTemporary Флаг - удалить каталог с временными файлами
     */
    private ticket(Path backupsPath,
                   Path releasesPath,
                   Path temporariesPath,
                   Path deployPath,
                   boolean deleteTemporary) {
        this.backupsPath = backupsPath != null ? backupsPath : Paths.get("./");
        this.releasesPath = releasesPath != null ? releasesPath : Paths.get("./");
        this.temporariesPath = temporariesPath != null ? temporariesPath : Paths.get("./");
        this.deployPath = deployPath != null ? deployPath : Paths.get("./");
        this.deleteTemporary = deleteTemporary;
    }

    /**
     * Получение каталога с бекапами
     *
     * @return  путь каталога с бекапами
     */
    public Path getBackupsPath() {
        return backupsPath;
    }

    /**
     * Получение каталога с релизами
     *
     * @return  путь каталога с релизами
     */
    public Path getReleasesPath() {
        return releasesPath;
    }

    /**
     * Получение каталога с временными файлами
     *
     * @return  путь каталога с временными файлами
     */
    public Path getTemporariesPath() {
        return temporariesPath;
    }

    /**
     * Получение каталога с развёрнутым приложением
     *
     * @return  путь каталога с развёрнутым приложением
     */
    public Path getDeployPath() {
        return deployPath;
    }

    /**
     * Необходимость удалить каталог с временными файлами по
     * завершению процедуры
     *
     * @return  удалить каталог
     */
    public boolean needDeleteTemporary() {
        return deleteTemporary;
    }

    /**
     * Получение допуска на бекап
     * Для бекапа требуются:
     * 1. Наличие каталога бекапа (можно создать в ходе работы)
     * 2. Наличие каталога деплоя с файлами (уже должен существовать)
     *
     * @param settings  Текущие настройки в хранилище настроек
     * @return          Разрешение
     */
    public static ticket getBackupAllow(configStorage settings) {
        Path backupsPath = null;
        Path deployPath = null;

        // Выполняем проверки
        try {
            backupsPath = settings.getBackupDirectory();
        } catch (ParameterNotFoundException err) {
            backupFailed("parameter \"" + BACKUPS_PATH + "\" in config file not set", err, settings.isDebug());
        } catch (FileNotFoundException err) {
            // При отсутствии каталога бекапа предлагаем его создать
            try {
                backupsPath = dialogs.answerCreateSelectPath("Backups path not exists", settings.getBackupDirectoryName());
                if (backupsPath == null) backupFailed("Cancelled by user");
            } catch (ParameterNotFoundException ignore) {
                // Здесь уже не может быть
            } catch (IOException ioErr) {
                backupFailed("I/O error", ioErr, settings.isDebug());
            }
        } catch (IncompatibleFileType err) {
            backupFailed("Backup location must be a directory type", err, settings.isDebug());
        }

        try {
            deployPath = settings.getDeployDirectory();
        } catch (ParameterNotFoundException err) {
            backupFailed("parameter \"" + DEPLOY_PATH + "\" in config file not set", err, settings.isDebug());
        } catch (FileNotFoundException err) {
            backupFailed("Deployment path not exists", err, settings.isDebug());
        } catch (IncompatibleFileType err) {
            backupFailed("Deployment path must be a directory type", err, settings.isDebug());
        }

        try {
            DirectoryScanner dlist = new DirectoryScanner();
            dlist.setBasedir(deployPath.toFile());
            dlist.scan();
            if (dlist.getIncludedFilesCount() == 0)
                backupFailed("Deployment path is empty");
        } catch (NullPointerException err) {
            backupFailed("Error get files list in deployment path");
        }

        if (settings.isDebug()) System.err.println("DEBUG: ticket: Successfully get ticket");
        return new ticket(backupsPath, null, null, deployPath, false);
    }

    /**
     * Ошибка при проверке на возможность выполнения бекапов
     *
     * @param cause Причина
     * @param error Ошибка
     * @param debug Режим отладки
     */
    private static void backupFailed(String cause, Exception error, boolean debug) {
        System.err.println("Unable to start backup: " + cause + "(" + error + ")");
        if (debug)
            error.printStackTrace();
        System.exit(EXIT_BACKUP_CHECK_ERROR);
    }

    /**
     * Ошибка при проверке на возможность выполнения бекапов (без возникновения
     * исключений)
     * @param cause Причина
     */
    private static void backupFailed(String cause) {
        System.err.println("Unable to start backup: " + cause);
        System.exit(EXIT_BACKUP_CHECK_ERROR);
    }
}
