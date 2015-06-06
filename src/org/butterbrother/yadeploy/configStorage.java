package org.butterbrother.yadeploy;

import org.ini4j.Ini;
import org.ini4j.InvalidFileFormatException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;

/**
 * ������ � ��������� ��������� ����������
 * ��������� ������� ��������� ��������� ������
 * ���������� ��� Ini4J � ���������� ��� ���������� ������ ������.
 */
public class configStorage
        extends Ini
        implements staticValues {
    private String releaseName; // ��� ������, ��� silent-������
    private int workMode;       // ����� ������
    private boolean debug;      // ����� �������

    /**
     * ����������� �������������.
     *
     * @param inputFile   ����������� ������
     * @param workMode    ����� ������, �� staticValues
     * @param releaseName ��� ������, ����� ���� ������
     * @param debug       ����� �������
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
     * ������ �������������, ��� ���������� � ������ ������ ��������
     * ����� ������ - ������ ����������� �������
     */
    private configStorage(boolean debug) {
        this.debug = debug;
        workMode = WORK_MODE_HELP;
        if (debug) System.out.println("DEBUG: only show help");
    }

    /**
     * ������������� ������ ������������
     *
     * @param args ��������� ��������� ������:
     *             [-d|--debug] [-c|--config ���� ������������] ����� ������ [��� ����� ��� ������,��������� ������]
     * @return ������ ������������
     */
    public static configStorage initialize(String args[]) {
        // �����
        boolean nextArgConfigFileName = false; // ��������� ����� ���� ������������
        boolean debug = false;  // ����� �������
        int workMode = WORK_MODE_NOTHING;  // ����� ������
        String configFileName = "yadeploy.ini"; // ��� ����� ������������
        String releaseName = null;

        // ������� ���������� ��������� ������
        for (String arg : args)
            switch (arg) {
                case "-c":
                case "--config":
                    // ��������-���������, ���������, ��� ��������� - ���� �������
                    nextArgConfigFileName = true;
                    break;
                case "-d":
                case "--debug":
                    // �������� ����������� ����� �������
                    if (!nextArgConfigFileName)
                        debug = true;
                    break;
                case "-h":
                case "--help":
                    // ����������� �������
                    if (!nextArgConfigFileName)
                        workMode = WORK_MODE_HELP;
                    break;
                case "b":
                case "backup":
                    // ���������� ������
                    // ����� ����� ���� ���������� ����������. ������ - ����� ��� ��� ������
                    if (!nextArgConfigFileName && workMode == WORK_MODE_NOTHING)
                        workMode = WORK_MODE_BACKUP;
                    break;
                case "r":
                case "restore":
                    // �������������� �� ������
                    if (!nextArgConfigFileName && workMode == WORK_MODE_NOTHING)
                        workMode = WORK_MODE_RESTORE;
                    break;
                case "i":
                case "install":
                    // ���������� ���������
                    if (!nextArgConfigFileName && workMode == WORK_MODE_NOTHING)
                        workMode = WORK_MODE_DEPLOY;
                    break;
                default:
                    // ���� ��� ��������-��������� ����� ������������
                    // �� ������� ���� �������� ����� ������ ������������
                    if (nextArgConfigFileName) {
                        configFileName = arg;
                        nextArgConfigFileName = false;
                        break;
                    }
                    // ���� ������� ����� ������, �� ��������� ��������
                    // ��������� ����� ������, ������ � �.�.
                    if (workMode != WORK_MODE_NOTHING) {
                        releaseName = arg;
                        break;
                    }
                    // ����� �������� ��� �� ��������
                    System.err.println("Argument " + arg + " unknown");
            }

        // � ����� ������� �������� ���������� �������

        // ���� ��������� ������ ������� - �� ��������� ������
        if (workMode == WORK_MODE_HELP) return new configStorage(debug);

        // ��������� ���� ������������
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

        // ��������, ��� �� ������� �� ����� �����, �� ��������� �������
        return new configStorage(debug);
    }

    private static void exitConfigFileError(String configFileName, String response, Throwable err, boolean debug) {
        System.err.println("Unable to open config file " + configFileName + " - " + response + " (" + err + ")");
        if (debug)
            err.printStackTrace();
        System.exit(EXIT_CONFIG_ERROR);
    }

    /**
     * ��������� ����� ������/������
     *
     * @return ��� ������ ��� ������
     */
    public String getReleaseName() {
        return releaseName;
    }

    /**
     * ��������� ������ ������
     *
     * @return ������� ����� ������, �������� staticValues
     */
    public int getWorkMode() {
        return workMode;
    }

    /**
     * ��������� ������� �������
     *
     * @return ������ �������
     */
    public boolean isDebug() {
        return debug;
    }
}
