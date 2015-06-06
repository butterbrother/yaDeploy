package org.butterbrother.yadeploy;

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
 */
public class ticket {
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
}
