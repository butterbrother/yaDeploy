package org.butterbrother.yadeploy;

/**
 * ������ ���������� �����
 */
public class start
    implements staticValues{
    public static void main(String args[]) {
        // ��������� ���� ������������ � ���������������� ��������� ��������� ������
        configStorage settings = configStorage.initialize(args);

        // ���������� ��� ���������� ��������� ������ �� ������ ������
        switch (settings.getWorkMode()) {
            case WORK_MODE_NOTHING:
                // ������� ���� - ������ �� ������
                System.err.println("No work mode set. See --help");
                System.exit(EXIT_NORMAL);
                break;
            case WORK_MODE_HELP:
                // ���������� �������
        }
    }
}
