package org.butterbrother.yadeploy;

import java.io.Closeable;
import java.util.Formatter;
import java.util.LinkedList;

/**
 * Для отображения прогресс-бара в виде ленты.
 * <p/>
 * Кроме основной функции хранит ошибки выполнения
 */
public class Progress implements Closeable, AutoCloseable {
    // Текущее значение счётчика
    private int clock = 0;
    // Число, необходимо для отображения деления прогресс-бара
    private int delayCount;
    // Ошибки выполнения операции, не прерывающие процесс, поэтому могут выводиться позднее
    private LinkedList<String> errorList = new LinkedList<>();

    /**
     * Инициализация. Отображает заголовок выполняемого действия и шкалу
     *
     * @param totalCount Общее число элементов (например, файлов)
     */
    public Progress(String progressName, int totalCount) {
        String progressHeader = "| 000% .... 025% .... 050% .... 075% .... 100% |";
        System.out.println(progressName);
        System.out.println(progressHeader);
        delayCount = totalCount / (progressHeader.length() - 1);
    }

    /**
     * Инкремент встроенного счётчика.
     * Отображает деление
     */
    public void inc() {
        if (++clock >= delayCount) {
            clock = 0;
            System.out.print("*");
        }
    }

    /**
     * Добавление строки ошибки
     *
     * @param errorMessage Сообщение об ошибке
     */
    public void addError(String errorMessage) {
        errorList.add(errorMessage);
    }

    /**
     * Вызывается, когда операция выполнена.
     * Отображает накопленные не критичные ошибки (если они были)
     * Так же выполняет сброс собственного состояния
     */
    @Override
    public void close() {
        System.out.println();
        if (errorList.size() > 0) {
            System.out.println("Errors:");
            Formatter errShow = new Formatter(System.out);
            for (String item : errorList) {
                errShow.format("-- %s\n", item);
            }
            clock = 0;
            errorList.clear();
        }
    }
}
