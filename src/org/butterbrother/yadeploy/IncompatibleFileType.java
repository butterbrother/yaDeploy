package org.butterbrother.yadeploy;

/**
 * Исключение - неверный тип файла
 */
public class IncompatibleFileType extends Exception {
    private String message; // Сообщение

    /**
     * Инициализация. Указывается ожидаемый и найденный тип файла/каталога
     *
     * @param expectedType ожидаемый тип
     * @param detectedType найденный тип
     */
    public IncompatibleFileType(String expectedType, String detectedType) {
        message = "Incompatible type, expected - " + expectedType + ", but detected - " + detectedType;
    }

    @Override
    public String toString() {
        return message;
    }
}
