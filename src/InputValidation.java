import java.time.*;
import java.time.format.DateTimeParseException;

final class InputValidation {
    private InputValidation() {}

    static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }

    static String label(String value) {
        String text = value.strip();
        require(!text.isEmpty() && text.length() <= 100 && text.chars().noneMatch(Character::isISOControl), "Название/имя: от 1 до 100 символов без управляющих символов");
        return text;
    }

    static int minute(String value, boolean allow24) {
        if (allow24 && value.equals("24:00")) return 1440;
        require(value.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]"), "Время должно иметь формат ЧЧ:ММ, например 09:00");
        LocalTime time = LocalTime.parse(value); return time.getHour() * 60 + time.getMinute();
    }

    static LocalDateTime date(String value) {
        require(value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}"), "Формат даты: ГГГГ-ММ-ДДTЧЧ:ММ");
        try { return LocalDateTime.parse(value); } catch (DateTimeParseException ex) { throw new IllegalArgumentException("Некорректная дата: " + value); }
    }
}
