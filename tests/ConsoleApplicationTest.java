import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Разбор консольных команд")
class ConsoleApplicationTest {
    @Test @DisplayName("Имя с пробелом в кавычках остаётся одним аргументом")
    void quotedName() {
        assertEquals(List.of("room-add", "Большой зал", "09:00", "18:00", "20"),
                ConsoleApplication.tokenize("room-add \"Большой зал\" 09:00 18:00 20"));
    }

    @Test @DisplayName("Незакрытые кавычки отклоняются")
    void unclosedQuote() {
        assertThrows(IllegalArgumentException.class, () -> ConsoleApplication.tokenize("book \"unfinished"));
    }
}
