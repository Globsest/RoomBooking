import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Правила бронирования")
class BookingServiceTest {
    @TempDir Path directory;
    private BookingService service;
    private Room room;

    @BeforeEach
    void setUp() throws Exception {
        service = new BookingService(directory);
        room = service.addRoom("Большой зал", "09:00", "18:00", "20");
    }

    @Test @DisplayName("Новое хранилище пустое")
    void emptyStorage() throws Exception {
        assertTrue(new BookingService(directory.resolve("empty")).rooms().isEmpty());
    }

    @Test @DisplayName("Название и параметры помещения сохраняются")
    void roomRoundTrip() throws Exception {
        assertEquals(room, new BookingService(directory).rooms().get(0));
    }

    @Test @DisplayName("Повторное название без учёта регистра запрещено")
    void duplicateName() {
        assertThrows(IllegalArgumentException.class,
                () -> service.addRoom("большой ЗАЛ", "09:00", "18:00", "20"));
    }

    @ParameterizedTest(name = "Некорректное помещение: {0}, {1}–{2}, мест {3}")
    @CsvSource({"' ',09:00,18:00,20", "A,19:00,18:00,20", "A,25:00,18:00,20",
            "A,09:00,18:00,0", "A,09:00,18:00,-1", "A,09:00,18:00,many"})
    void invalidRoom(String name, String open, String close, String capacity) {
        assertThrows(IllegalArgumentException.class, () -> service.addRoom(name, open, close, capacity));
    }

    @Test @DisplayName("Бронь сохраняется между экземплярами сервиса")
    void persistence() throws Exception {
        var booking = service.book(room.id(), "2026-11-01T10:00", "2026-11-01T11:00", "Иван");
        assertEquals(java.util.List.of(booking), new BookingService(directory).bookings(null));
    }

    @ParameterizedTest(name = "Пересечение: {0}–{1}")
    @CsvSource({"10:00,11:00", "09:30,10:30", "10:30,11:30", "10:10,10:50", "09:00,12:00"})
    void overlappingBooking(String start, String end) throws Exception {
        service.book(room.id(), "2026-11-01T10:00", "2026-11-01T11:00", "Иван");
        var error = assertThrows(IllegalArgumentException.class, () -> service.book(room.id(),
                "2026-11-01T" + start, "2026-11-01T" + end, "Пётр"));
        assertTrue(error.getMessage().contains("уже забронировано"));
        assertEquals(1, service.bookings(null).size());
    }

    @Test @DisplayName("Соседние интервалы и границы рабочего дня разрешены")
    void adjacentIntervals() throws Exception {
        service.book(room.id(), "2026-11-01T10:00", "2026-11-01T11:00", "Иван");
        service.book(room.id(), "2026-11-01T09:00", "2026-11-01T10:00", "Иван");
        service.book(room.id(), "2026-11-01T11:00", "2026-11-01T18:00", "Иван");
        assertEquals(3, service.bookings(room.id()).size());
    }

    @ParameterizedTest(name = "Недопустимый период: {0} — {1}")
    @CsvSource({
            "2026-11-02T08:59,2026-11-02T10:00",
            "2026-11-02T17:00,2026-11-02T18:01",
            "2026-11-02T17:00,2026-11-03T10:00",
            "2026-11-02T10:00,2026-11-02T10:00",
            "2026-11-02T11:00,2026-11-02T10:00",
            "2026-02-30T10:00,2026-03-01T10:00"})
    void invalidPeriod(String start, String end) {
        assertThrows(IllegalArgumentException.class, () -> service.book(room.id(), start, end, "Иван"));
    }

    @Test @DisplayName("Нельзя забронировать неизвестное помещение")
    void unknownRoom() {
        assertThrows(IllegalArgumentException.class,
                () -> service.book("missing", "2026-11-02T10:00", "2026-11-02T11:00", "Иван"));
    }

    @Test @DisplayName("Владелец не может быть пустым")
    void blankOwner() {
        assertThrows(IllegalArgumentException.class,
                () -> service.book(room.id(), "2026-11-02T10:00", "2026-11-02T11:00", " "));
    }

    @Test @DisplayName("Разные помещения независимы, круглосуточная бронь может длиться несколько дней")
    void independentRooms() throws Exception {
        service.book(room.id(), "2026-11-01T10:00", "2026-11-01T11:00", "Иван");
        var other = service.addRoom("Другой", "00:00", "24:00", "1");
        service.book(other.id(), "2026-11-01T10:00", "2026-11-03T11:00", "Иван");
        assertEquals(1, service.bookings(other.id()).size());
        assertEquals(2, service.bookings(null).size());
    }

    @Test @DisplayName("Чужое имя не позволяет отменить бронь")
    void wrongOwner() throws Exception {
        var booking = service.book(room.id(), "2026-11-01T10:00", "2026-11-01T11:00", "Иван");
        assertThrows(IllegalArgumentException.class, () -> service.cancel(booking.id(), "Пётр"));
        assertEquals(1, service.bookings(null).size());
    }

    @Test @DisplayName("Отмена неизвестной брони отклоняется")
    void unknownBooking() {
        assertThrows(IllegalArgumentException.class, () -> service.cancel("missing", "Иван"));
    }

    @Test @DisplayName("После отмены период снова доступен")
    void cancellationReleasesInterval() throws Exception {
        var booking = service.book(room.id(), "2026-11-01T10:00", "2026-11-01T11:00", "Иван");
        service.cancel(booking.id(), "Иван");
        assertTrue(service.bookings(null).isEmpty());
        var replacement = service.book(room.id(), "2026-11-01T10:00", "2026-11-01T11:00", "Пётр");
        assertEquals(java.util.List.of(replacement), service.bookings(null));
    }
}
