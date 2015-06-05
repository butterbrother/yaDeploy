package org.butterbrother.yadeploy;

/**
 * ��������� ��������, ���������.
 * ����� ��� ����� ����������
 */
public interface staticValues {
    // ������ ����������
    String yaDeployVersion = "0.0.1a";
    /*
     * ������ ������
     */
    int WORK_MODE_NOTHING = 0;  // ������ �� ������
    int WORK_MODE_BACKUP = 1;   // ������ �����
    int WORK_MODE_RESTORE = 2;  // ��������������� �� ������
    int WORK_MODE_DEPLOY = 3;   // ������������� ����������
    int WORK_MODE_HELP = 4;     // ���������� �������

    /*
     * ���� ��������
     */
    int EXIT_NORMAL = 0;        // ���������� ����������
    int EXIT_CONFIG_ERROR = 1;  // ������ ������ � ��������� ����� ������������

    /*
     * ��������� � ini-�����
     */
    // [main]
    String MAIN_SECTION = "main";
    // releases = ������� � ��������, ������������ ��������
    String RELEASES_PATH = "releases";
    // backups = ������� � �������� - ������������
    String BACKUPS_PATH = "backups";
    // temp = ������� � ���������� ������� - �� ������������. ���� �� �����������, ������ � ��������� TEMP
    String TMP_PATH = "temp";

    /*
     * ������ �������
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
