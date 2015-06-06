package org.butterbrother.selectFSItem;

import java.io.Console;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ������������ ��������� � �������, ������� ������
 */
public class selectItem {

    /**
     * ����� ������� (�������� ���������)
     *
     * @param startPosition ��������� �������
     * @param mask          ����� �����, �������� Java Regexp
     * @param dirOnly       ���������� ������ ��������
     * @return ����� ������������ ���� null, ���� ������������ ���������
     * @throws IOException ���� ������ �����
     */
    public static Path selectFile(String startPosition, String mask, boolean dirOnly) throws IOException {
        // ������ ������ ��������� ������� ������ ������
        Path navPath = Paths.get(startPosition);
        // �������� � �����������, ���� ������ ���
        if (Files.notExists(navPath))
            throw new IOException("File not exists");

        navPath = navPath.toAbsolutePath();
        // ���� ������ ���� - �������� ��� ����������� ����������
        if (!Files.isDirectory(navPath))
            navPath = navPath.getParent();

        Formatter listEl = new Formatter(System.out); // ��������������� ���������
        Date fileDate = new Date();           // � ���� ��� ����������� ���� ������
        Console input = System.console();       // ���������������� ����
        // ������ �������� ���������
        do {
            listEl.format("\n< %s >\n", navPath.toAbsolutePath().toString()); // ���������� ������� �������
            // �����������
            if (dirOnly) {
                listEl.format("Select directory:\n");
            } else {
                listEl.format("Select file or directory:\n");
            }

            // ������ ��������� � ��������
            LinkedHashMap<Integer, Path> itemList = new LinkedHashMap<>();
            int count = 0;  // ��� ������������ ���������
            try (DirectoryStream<Path> list = Files.newDirectoryStream(navPath, new dirStreamFilter<>(mask, dirOnly))) {
                // ��������� ������ �� �������� ��������
                for (Path item : list)
                    itemList.put(++count, item);
            } catch (DirectoryIteratorException ignore) {
            } catch (AccessDeniedException ignore) {
                listEl.format("<Access denied, unable to enumerate files list>\n");
                listEl.format("<You can go to parent directory or enter path manually>\n");
            }


            // ���������� ������ � �������������� ���������
            listEl.format("%3s  %-30s  %5s  %16s  %16s \n", "NUM", "File Name", "Attr", "Created", "Modified");
            if (itemList.size() == 0)
                listEl.format("<Empty>\n");
            for (Map.Entry<Integer, Path> item : itemList.entrySet()) {
                try {
                    // ���������� ����� � ��� �����
                    listEl.format("%3d  %-30s  ", item.getKey(), item.getValue().getFileName().toString());

                    // ��������� ��������
                    BasicFileAttributes attr = Files.readAttributes(item.getValue(), BasicFileAttributes.class);

                    // � ��-������� �� �������
                    // ��� �����
                    if (attr.isDirectory())
                        listEl.format("d");    // �������
                    else if (attr.isRegularFile())
                        listEl.format("-");    // ����
                    else if (attr.isSymbolicLink())
                        listEl.format("l");    // �������
                    else if (attr.isOther())
                        listEl.format("o");    // ������
                    else
                        listEl.format("u");    // ����������� ���

                    // �����
                    if (Files.isReadable(item.getValue())) {
                        listEl.format("r");
                    } else {
                        listEl.format("-");
                    }
                    if (Files.isWritable(item.getValue())) {
                        listEl.format("w");
                    } else {
                        listEl.format("-");
                    }
                    if (Files.isExecutable(item.getValue())) {
                        listEl.format("x");
                    } else {
                        listEl.format("-");
                    }
                    if (Files.isHidden(item.getValue())) {
                        listEl.format("h");
                    } else {
                        listEl.format("-");
                    }
                    listEl.format("  ");

                    // ���� ��������
                    fileDate.setTime(attr.creationTime().toMillis());
                    listEl.format("%TY-%<Tm-%<Td %<TH:%<TM  ", fileDate);

                    // ���� �����������
                    fileDate.setTime(attr.lastModifiedTime().toMillis());
                    listEl.format("%TY-%<Tm-%<Td %<TH:%<TM  ", fileDate);

                    // ������� ������ � �����
                    listEl.format("\n");
                } catch (IOException attrErr) {
                    listEl.format("<Access denied>\n");
                }
            }

            // ����� ���������� ����� ���������
            listEl.format("Enter number, \"o\" to select current directory, \"u\" or \"..\" switch to parent directory, \"m\" to manual enter, or \"q\" to exit >> ");
            String userInput = input.readLine();
            switch (userInput) {
                // ������� �������
                case "o":
                    return navPath;
                // �����, ����� null
                case "q":
                    return null;
                // �� ������� �����
                case "u":
                case "..":
                    if ((navPath.getParent() == null)) {
                        listEl.format("Unable to switch parrent\n");
                    } else {
                        navPath = navPath.getParent();
                    }
                    break;
                // �������� ���� �������
                case "m":
                    listEl.format("Enter path >> ");
                    Path manualPath = Paths.get(input.readLine()).toAbsolutePath();
                    if (Files.notExists(manualPath)) {
                        listEl.format("File or path not exists\n");
                    } else {
                        return manualPath;
                    }
                    break;
                default:
                    try {
                        int numItem = Integer.parseInt(userInput);
                        // �������� ����-���� �� ������������. ����� ���� null
                        navPath = itemList.get(numItem) != null ? itemList.get(numItem) : navPath;
                        // ���� ������ ���� - ����� � ����� ����
                        if (!Files.isDirectory(navPath))
                            return navPath;
                        // ����� ��������� �����
                    } catch (NumberFormatException | IndexOutOfBoundsException err) {
                        listEl.format("Nope, try again, bro\n");
                    }
            }
        } while (true);
    }
}
