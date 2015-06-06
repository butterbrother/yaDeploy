package org.butterbrother.selectFSItem;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * ���������� ������
 */
public class dirStreamFilter<T extends Path>
        implements DirectoryStream.Filter<T> {
    private boolean dirOnly;
    private String filter;

    /**
     * ������������� ������� � ����������� ��
     * ����� �����/����������
     *
     * @param filter  ������
     * @param dirOnly ���� - ������ ��������,
     *                �������� ������� ����� ��������������
     */
    public dirStreamFilter(String filter, boolean dirOnly) {
        this.dirOnly = dirOnly;
        this.filter = filter;
    }

    /**
     * ���� ����������
     *
     * @param entry ������� - ���� ���� �������
     * @return ������� �������
     * @throws IOException
     */
    @Override
    public boolean accept(T entry) throws IOException {
        // ������ ���������� ��������
        if (Files.isDirectory(entry)) return true;
        // ����� - ������ ��������, �� ���������� �����
        if (dirOnly && !Files.isDirectory(entry)) return false;
        // ���� ������ - ��� �����, �� ���������� ���
        if (filter.equals("*")) return true;

        // �������� ��� ����� � ��������� �� �����
        return Pattern.compile(filter, Pattern.CASE_INSENSITIVE).matcher(entry.getFileName().toString()).matches();
    }
}
