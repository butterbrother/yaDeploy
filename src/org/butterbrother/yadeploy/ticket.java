package org.butterbrother.yadeploy;

import org.apache.tools.ant.DirectoryScanner;
import org.butterbrother.selectFSItem.dialogs;

import java.io.File;
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
 * <p/>
 * Некоторые проверки требуют несколько разрешений для выполнения какого-либо действия
 */
public class ticket
    implements staticValues{

    // Каталог временных файлов
    private Path temporariesPath;       // В виде Path для nio
    private File temporariesFile;       // В виде File. Требуется для традиционного api и ant-api
    private String temporariesFullName; // В виде полного строкового пути
    private String temporariesName;     // Имя каталога временных файлов

    // Каталог исходных файлов
    private Path sourcePath;            // В виде Path для nio
    private File sourceFile;            // В виде File. Требуется для традиционного api и ant-api
    private String sourceFullName;      // В виде полного строкового пути
    private String sourceName;          // Имя исходного каталога

    // Конечный каталог
    private Path destinationPath;       // В виде Path для nio
    private File destinationFile;       // В виде File. Требуется для традиционного api и ant-api
    private String destinationFullName; // В виде полного строкового пути
    private String destinationName;     // Имя конечного каталога

    private boolean deleteTemporary;    // Удалить временный каталог после завершения действия

    /**
     * Внутренняя инициализация. Получить разрешение можно только пройдя
     * соответствующие проверки
     *
     * @param source            Источник
     * @param destination       Приёмник
     * @param temporary         Временный каталог
     * @param deleteTemporary   Указание на необходимость удаления временного каталога
     */
    private ticket(Path source, Path destination, Path temporary, boolean deleteTemporary) {
        // Получаем пути
        this.sourcePath = source;
        this.destinationPath = destination;
        this.temporariesPath = temporary != null ? temporary : Paths.get("./").toAbsolutePath();
        this.deleteTemporary = deleteTemporary;

        // Получаем File из путей
        this.sourceFile = source.toFile();
        this.destinationFile = destination.toFile();
        this.temporariesFile = this.temporariesPath.toFile();

        // Получаем полные строковые пути
        this.sourceFullName = source.toString();
        this.destinationFullName = destination.toString();
        this.temporariesFullName = temporary != null? temporary.toString() : "";

        // Получаем имена файлов
        this.sourceName = source.getFileName().toString();
        this.destinationName = destination.getFileName().toString();
        this.temporariesName = this.temporariesPath.getFileName().toString();
    }

    /**
     * Получить каталог источника действия
     *
     * @return  Каталог источника действия
     */
    public Path getSourcePath() { return sourcePath; }

    /**
     * Получить каталог приёмника действия
     *
     * @return  Каталог приёмника действия
     */
    public Path getDestinationPath() { return destinationPath; }

    /**
     * Получить строковый путь каталога источника
     *
     * @return  Строковый путь каталога источника
     */
    public String getSourceFullName() { return sourceFullName; }

    /**
     * Получить строковое имя источника (без полного пути)
     *
     * @return  Имя источника
     */
    public String getSourceName() { return sourceName; }

    /**
     * Получить строковое имя каталога временных файлов (без полного пути)
     *
     * @return  Имя каталога временных файлов
     */
    public String getTemporariesName() { return temporariesName; }

    /**
     * Получить строковое имя каталога назначения (без полного пути)
     *
     * @return  Имя каталога назначения
     */
    public String getDestinationName() { return destinationName; }

    /**
     * Получить строковый путь каталога приёмника
     *
     * @return  Строковый путь каталога приёмника
     */
    public String getDestinationFullName() { return destinationFullName; }

    /**
     * Получить строковый путь каталога временных файлов
     *
     * @return  Строковый путь каталога временных файлов
     */
    public String getTemporariesFullName() { return temporariesFullName; }

    /**
     * Получение каталога с временными файлами
     *
     * @return  путь каталога с временными файлами
     */
    public Path getTemporariesPath() {
        return temporariesPath;
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
     * Получение пути к каталогу временных файлов в виде File.
     * Для традиционного API и ANT-API
     *
     * @return  Файл каталога временных файлов
     */
    public File getTemporariesFile() { return temporariesFile; }

    /**
     * Получение пути к каталогу источника в виде File.
     * Для традиционного API и ANT-API
     *
     * @return  Файл каталога источника
     */
    public File getSourceFile() { return sourceFile; }

    /**
     * Получение пути к каталогу получателя в виде File.
     * Для традиционного API и ANT-API
     *
     * @return  Файл каталога получателя
     */
    public File getDestinationFile() { return destinationFile; }

    /**
     * Получение допуска на бекап
     * </p>
     * Для бекапа требуются:
     * 1. Наличие каталога бекапа (можно создать в ходе работы)
     * 2. Наличие каталога деплоя с файлами (уже должен существовать)
     *
     * @param settings  Текущие настройки в хранилище настроек
     * @return          Разрешение
     */
    public static ticket getBackupOnlyAllow(configStorage settings) {

        // Выполняем проверки
        Path backupsPath = requiredBackupPath(settings);
        Path deployPath = requiredDeploymentPath(settings);
        deploymentPathNotBeEmpty(deployPath);

        return new ticket(deployPath, backupsPath, null, false);
    }

    /**
     * Получение допуска деплоя
     *
     * Для деплоя требуется:
     * 1. Каталог с релизами - должен быть
     * 2. Каталог с деплоями - должен быть указан путь
     *
     * @param settings  Текущие настройки в хранилище настроек
     * @return          Разрешение
     */
    public static ticket getDeployAllow(configStorage settings) {

    }

    /**
     * Получение допуска на выполнение предварительного бекапа
     * перед каким-либо последующим действием (восстановление или деплой)
     * </p>
     * Требуется:
     * 1. Наличие каталога бекапа (можно создать в ходе работы)
     * Не обязательно:
     * 2. Наличие каталога деплоя. Отсутствие создаст исключение, которое
     * не является причиной отказа
     *
     * @param settings  Текущие настройки в хранилище настроек
     * @return          Разрешение
     * @throws ActionNotAvailable   бекап невозможен, нет каталога деплоя
     */
    public static ticket getBackupBeforeAllow(configStorage settings) throws ActionNotAvailable {
        // Выполняем проверки
        Path backupsPath = requiredBackupPath(settings);
        Path deployPath = desirableDeploymentPath(settings);
        desirableDeploymentPathNotBeEmpty(deployPath);

        return new ticket(deployPath, backupsPath, null, false);
    }

    /**
     * Проверка условия, что каталог с деплоем не пустой (есть файлы)
     *
     * @param deploy    Каталог с деплоем
     */
    public static void deploymentPathNotBeEmpty(Path deploy) {
        try {
            DirectoryScanner dlist = new DirectoryScanner();
            dlist.setBasedir(deploy.toFile());
            dlist.scan();
            if (dlist.getIncludedFilesCount() == 0)
                backupFailed("Deployment path is empty");
        } catch (NullPointerException err) {
            backupFailed("Error get files list in deployment path");
        }
    }

    /**
     * Проверка условия, что каталог с деплоем не пустой (есть файлы).
     *
     * @param deploy    Каталог с деплоем
     * @throws ActionNotAvailable   бекап невозможен, каталог деплоя пуст
     */
    public static void desirableDeploymentPathNotBeEmpty(Path deploy) throws ActionNotAvailable {
        try {
            if (scanDir(deploy) == 0)
                throw new ActionNotAvailable();
        } catch (NullPointerException err) {
            throw new ActionNotAvailable();
        }
    }

    /**
     * Сканирование каталога и подсчёт имеющихся файлов
     *
     * @param path  Путь
     * @return      Число файлов в каталоге
     */
    private static int scanDir(Path path) {
        DirectoryScanner dlist = new DirectoryScanner();
        dlist.setBasedir(path.toFile());
        dlist.scan();
        return dlist.getIncludedFilesCount();
    }

    /**
     * Получение каталога деплоя, каталог обязательно должен быть
     *
     * @param settings  Файл и хранилище настроек
     * @return          Путь к каталогу деплоя
     */
    private static Path requiredDeploymentPath(configStorage settings) {
        try {
            return settings.getDeployDirectory();
        } catch (ParameterNotFoundException err) {
            backupFailed("parameter \"[" + DEPLOY_SECTION + "]\"->\""+ DEPLOY_LOCATION + "\" in config file not set", err, settings.isDebug());
        } catch (FileNotFoundException err) {
            backupFailed("Deployment path not exists", err, settings.isDebug());
        } catch (IncompatibleFileType err) {
            backupFailed("Deployment path must be a directory type", err, settings.isDebug());
        }

        // До этой части код доходить не должен
        System.exit(EXIT_GENERAL_ERROR);
        return Paths.get("./");
    }

    /**
     * Получение каталога деплоя. Каталогу на момент запроса существовать не обязательно.
     *
     * @param settings  Файл и хранилище настроек
     * @return          Путь к каталогу деплоя
     * @throws ActionNotAvailable   Отсутствие каталога деплоя и невозможность выполнения
     * предварительной операции
     */
    private static Path desirableDeploymentPath(configStorage settings) throws ActionNotAvailable {
        try {
            return settings.getDeployDirectory();
        } catch (ParameterNotFoundException | FileNotFoundException | IncompatibleFileType err) {
            throw new ActionNotAvailable();
        }
    }

    /**
     * Получение каталога бекапов, каталог бекапов обязателен
     * </p>
     * Если каталог бекапов не существует - будет предложено
     * указать его вручную, либо создать
     *
     * @param settings      Файл и хранилище настроек
     * @return              Путь к каталогу бекапов
     */
    private static Path requiredBackupPath(configStorage settings) {
        try {
            return settings.getBackupDirectory();
        } catch (ParameterNotFoundException err) {
            backupFailed("parameter \"[" + BACKUPS_SECTION + "]\"->\"" + BACKUPS_LOCATION + "\" in config file not set", err, settings.isDebug());
        } catch (FileNotFoundException err) {
            // При отсутствии каталога бекапа предлагаем его создать
            try {
                Path backupsPath = dialogs.answerCreateSelectPath("Backups path not exists", settings.getBackupDirectoryName());
                if (backupsPath == null) backupFailed("Cancelled by user");
                return backupsPath;
            } catch (ParameterNotFoundException ignore) {
                // Здесь уже не может быть
            } catch (IOException ioErr) {
                backupFailed("I/O error", ioErr, settings.isDebug());
            }
        } catch (IncompatibleFileType err) {
            backupFailed("Backup location must be a directory type", err, settings.isDebug());
        }

        // До этой части код не доходит. И не должен
        System.exit(EXIT_GENERAL_ERROR);
        return Paths.get("./");
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
