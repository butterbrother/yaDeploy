package org.butterbrother.yadeploy;

import org.ini4j.Ini;
import org.ini4j.InvalidFileFormatException;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.*;

/**
 * Хранит и загружает настройки приложения
 * Выполняет парсинг элементов командной строки
 * Расширение над Ini4J с валидацией под конкретные режимы работы.
 */
public class configStorage
        extends Ini
        implements staticValues {
    private String releaseName; // Имя релиза, для silent-режима
    private int workMode;       // Режим работы
    private boolean debug;      // Режим отладки

    /**
     * Стандартная инициализация.
     *
     * @param inputFile   считываемый конфиг
     * @param workMode    режим работы, из staticValues
     * @param releaseName имя релиза, может быть пустым
     * @param debug       режим отладки
     * @throws IOException
     */
    private configStorage(Reader inputFile, int workMode, String releaseName, boolean debug) throws IOException {
        super(inputFile);
        this.releaseName = releaseName != null ? releaseName : "";
        this.workMode = workMode;
        this.debug = debug;
        if (debug) System.out.println("DEBUG: config module:" + this);
    }

    /**
     * Пустая инициализация, для валидатора и ошибок чтения конфигов
     * Режим работы - только отображение справки
     */
    private configStorage(boolean debug) {
        this.debug = debug;
        workMode = WORK_MODE_HELP;
        if (debug) System.out.println("DEBUG: only show help");
    }

    /**
     * Инициализация модуля конфигурации
     *
     * @param args агрументы командной строки:
     *             [-d|--debug] [-c|--config файл конфигурации] режим работы [имя файла для отката,установки релиза]
     * @return модуль конфигурации
     */
    public static configStorage initialize(String args[]) {
        // Флаги
        boolean nextArgConfigFileName = false; // Следующим будет файл конфигурации
        boolean debug = false;  // Режим отладки
        int workMode = WORK_MODE_NOTHING;  // Режим работы
        String configFileName = "yadeploy.ini"; // Имя файла конфигурации
        String releaseName = null;

        // Парсинг аргументов командной строки
        for (String arg : args)
            switch (arg) {
                case "-c":
                case "--config":
                    // Аргумент-указатель, указывает, что следующий - файл конфига
                    nextArgConfigFileName = true;
                    break;
                case "-d":
                case "--debug":
                    // Аргумент переключает режим отладки
                    if (!nextArgConfigFileName)
                        debug = true;
                    break;
                case "-h":
                case "--help":
                    // отображение справки
                    if (!nextArgConfigFileName)
                        workMode = WORK_MODE_HELP;
                    break;
                case "b":
                case "backup":
                    // Выполнение бекапа
                    // Режим может быть установлен однократно. Дальше - номер или имя релиза
                    if (!nextArgConfigFileName && workMode == WORK_MODE_NOTHING)
                        workMode = WORK_MODE_BACKUP;
                    break;
                case "r":
                case "restore":
                    // Восстановление из бекапа
                    if (!nextArgConfigFileName && workMode == WORK_MODE_NOTHING)
                        workMode = WORK_MODE_RESTORE;
                    break;
                case "i":
                case "install":
                    // Выполнение установки
                    if (!nextArgConfigFileName && workMode == WORK_MODE_NOTHING)
                        workMode = WORK_MODE_DEPLOY;
                    break;
                default:
                    // Если был аргумент-указатель файла конфигурации
                    // то считаем этот аргумент самим файлом конфигурации
                    if (nextArgConfigFileName) {
                        configFileName = arg;
                        nextArgConfigFileName = false;
                        break;
                    }
                    // Если указали режим работы, то следующий параметр
                    // указывает номер релиза, бекапа и т.п.
                    if (workMode != WORK_MODE_NOTHING) {
                        releaseName = arg;
                        break;
                    }
                    // Иначе параметр нам не известен
                    System.err.println("Argument " + arg + " unknown");
            }

        // С этого момента возможна автономная отладка

        // Если требуется только справка - не считываем конфиг
        if (workMode == WORK_MODE_HELP) return new configStorage(debug);

        // Считываем файл конфигурации
        try (BufferedReader realFile = Files.newBufferedReader(Paths.get(configFileName), Charset.forName("UTF-8"))) {
            return new configStorage(realFile, workMode, releaseName, debug);
        } catch (InvalidFileFormatException err) {
            exitConfigFileError(configFileName, "invalid file format", err, debug);
        } catch (NoSuchFileException err) {
            exitConfigFileError(configFileName, "file not found", err, debug);
        } catch (AccessDeniedException err) {
            exitConfigFileError(configFileName, "access denies", err, debug);
        } catch (IOException err) {
            exitConfigFileError(configFileName, "I/O error", err, debug);
        }

        // Пустышка, код не доходит до этого места, но валидатор требует
        return new configStorage(debug);
    }

    private static void exitConfigFileError(String configFileName, String response, Throwable err, boolean debug) {
        System.err.println("Unable to open config file " + configFileName + " - " + response + " (" + err + ")");
        if (debug)
            err.printStackTrace();
        System.exit(EXIT_CONFIG_ERROR);
    }

    /**
     * Получение имени релиза/бекапа
     *
     * @return имя релиза или бекапа
     */
    public String getReleaseName() {
        return releaseName;
    }

    /**
     * Получение режима работы
     *
     * @return текущий режим работы, согласно staticValues
     */
    public int getWorkMode() {
        return workMode;
    }

    /**
     * Получение статуса отладки
     *
     * @return Статус отладки
     */
    public boolean isDebug() {
        return debug;
    }

    /**
     * Возвращает имя каталога резервных копий
     *
     * @return Имя каталога резервных копий
     * @throws ParameterNotFoundException Параметр не найден в ini
     */
    public String getBackupDirectoryName() throws ParameterNotFoundException {
        return getParameter(MAIN_SECTION, BACKUPS_PATH);
    }

    /**
     * Возвращает имя каталога с релизом
     *
     * @return Имя каталога с релизом
     * @throws ParameterNotFoundException Параметр не найден в ini
     */
    public String getReleaseDirectoryName() throws ParameterNotFoundException {
        return getParameter(MAIN_SECTION, RELEASES_PATH);
    }

    /**
     * Возвращает имя каталога с временными файлами
     *
     * @return Имя каталога с временными файлами
     * @throws ParameterNotFoundException Параметр не найден в ini
     */
    public String getTemporaryDirectoryName() throws ParameterNotFoundException {
        return getParameter(MAIN_SECTION, TMP_PATH);
    }

    /**
     * Возвращает имя каталога с активным приложением
     *
     * @return Имя каталога с активным приложением
     * @throws ParameterNotFoundException Параметр не найден в ini
     */
    public String getDeployDirectoryName() throws ParameterNotFoundException {
        return getParameter(MAIN_SECTION, DEPLOY_PATH);
    }

    /**
     * Возвращает путь к каталогу резервных копий.
     * Путь проверяется на наличие
     *
     * @return Путь каталога резервной копии
     * @throws ParameterNotFoundException отсутствие параметра пути к бекапам в ini-файле
     * @throws FileNotFoundException      каталог не существует
     * @throws IncompatibleFileType       указанный файл оказался не каталогом
     */
    public Path getBackupDirectory()
            throws ParameterNotFoundException, FileNotFoundException, IncompatibleFileType {
        return getDirectoryPath(getBackupDirectoryName(), "backup directory");
    }

    /**
     * Возвращает путь к каталогу релиза
     * Путь проверяется на наличие
     *
     * @return Путь каталога резервной копии
     * @throws ParameterNotFoundException отсутствие параметра пути к бекапам в ini-файле
     * @throws FileNotFoundException      каталог не существует
     * @throws IncompatibleFileType       указанный файл оказался не каталогом
     */
    public Path getReleaseDirectory()
            throws ParameterNotFoundException, FileNotFoundException, IncompatibleFileType {
        return getDirectoryPath(getReleaseDirectoryName(), "release directory");
    }

    /**
     * Возвращает путь к каталогу разворачиваемого приложения
     * Путь проверяется на наличие
     *
     * @return Путь каталога резервной копии
     * @throws ParameterNotFoundException отсутствие параметра пути к бекапам в ini-файле
     * @throws FileNotFoundException      каталог не существует
     * @throws IncompatibleFileType       указанный файл оказался не каталогом
     */
    public Path getDeployDirectory()
            throws ParameterNotFoundException, FileNotFoundException, IncompatibleFileType {
        return getDirectoryPath(getDeployDirectoryName(), "deploy directory");
    }

    /**
     * Возвращает путь к каталогу временных файлов
     * Путь проверяется на наличие. Если отсутствует либо не указан в ini - будет создан
     * во временном каталоге по-умолчанию
     *
     * @return Путь к каталогу временных файлов
     * @throws IOException Ошибка ввода-вывода
     */
    public Path getTemporaryDirectory() throws IOException {
        try {
            return getDirectoryPath(getTemporaryDirectoryName(), "temporary directory");
        } catch (ParameterNotFoundException | IncompatibleFileType | FileNotFoundException ignore) {
            // Игнорируем все ошибки и создаём временный каталог по-умолчанию
            if (debug) {
                System.out.println("DEBUG: temporary directory request failed: " + ignore);
                ignore.printStackTrace();
            }
            return Files.createTempDirectory("yadeploy_");
        }
    }

    /**
     * Возвращает путь (объект) к указанному каталогу по его пути/имени.
     * Путь проверяется на наличие, и что он действительно является каталогом
     *
     * @param name    Имя, путь к каталогу
     * @param details Краткое описание каталога
     * @return Путь к каталогу
     * @throws ParameterNotFoundException
     * @throws FileNotFoundException
     * @throws IncompatibleFileType
     */
    private Path getDirectoryPath(String name, String details)
            throws ParameterNotFoundException, FileNotFoundException, IncompatibleFileType {
        // Создаём путь
        Path directoryPath = Paths.get(name);
        // Получаем абсолютный путь для возможности проверки
        directoryPath = directoryPath.toAbsolutePath();
        // Проверяем на существование
        if (Files.notExists(directoryPath)) {
            throw new FileNotFoundException(details + directoryPath.getFileName() + " not found");
        }
        // И что путь действительно каталог
        if (Files.isDirectory(directoryPath)) {
            return directoryPath;
        } else {
            throw new IncompatibleFileType("directory", "file");
        }
    }

    /**
     * Выполняет поиск в Ini-файле по имени параметра
     * с указанием секции расположения. При этом игнорируется регистр.
     * При наличии совпадения возвращается строковое значение параметра.
     * При отсутствии - исключение.
     *
     * @param sectionName   Предполагаемое имя секции
     * @param parameterName Предполагаемое имя параметра
     * @return Значение параметра
     * @throws ParameterNotFoundException параметр не найден по имени в нужной
     *                                    секции
     */
    public String getParameter(String sectionName, String parameterName)
            throws ParameterNotFoundException {
        // Последовательно перебираем имена секций, ищем совпадение
        for (String section : this.keySet()) {
            if (section.equalsIgnoreCase(sectionName)) {
                // Если искомая секция найдена - так же перебираем имена параметров
                // в сеции
                for (String parameter : this.get(section).keySet()) {
                    if (parameter.equalsIgnoreCase(parameterName)) {
                        // Возвращаем значение параметра
                        return this.get(section).get(parameter);
                    }
                }
            }
        }
        // Если мы добрались сюда - параметр не найден
        throw new ParameterNotFoundException();
    }
}
