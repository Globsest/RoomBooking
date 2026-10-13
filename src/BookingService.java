import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

public final class BookingService {

    private final BookingRepository repository;

    public BookingService(Path directory) { this(new BookingRepository(directory)); }
    public BookingService(BookingRepository repository) { this.repository = Objects.requireNonNull(repository); }


    public Room addRoom(String name, String open, String close, String capacity) throws IOException {
        String validName = InputValidation.label(name);
        int from = InputValidation.minute(open, false), to = InputValidation.minute(close, true);
        InputValidation.require(from < to, "Открытие должно быть раньше закрытия. Для круглосуточного режима: 00:00 24:00.");
        int seats;
        try { seats = Integer.parseInt(capacity); } catch (NumberFormatException ex) { throw new IllegalArgumentException("Вместимость должна быть целым числом"); }
        InputValidation.require(seats > 0, "Вместимость должна быть положительной");
        return repository.transaction(true, state -> {
            InputValidation.require(state.rooms.stream().noneMatch(r -> r.name().equalsIgnoreCase(validName)), "Помещение с таким названием уже существует");
            Room room = new Room(UUID.randomUUID().toString(), validName, from, to, seats);
            state.rooms.add(room); return room;
        });
    }

    public Booking book(String roomId, String start, String end, String owner) throws IOException {
        LocalDateTime from = InputValidation.date(start), to = InputValidation.date(end);
        String user = InputValidation.label(owner);
        InputValidation.require(from.isBefore(to), "Начало должно быть раньше окончания");
        return repository.transaction(true, state -> {
            Room room = findRoom(state, roomId);
            if (!(room.open() == 0 && room.close() == 1440)) {
                LocalDateTime opening = from.toLocalDate().atStartOfDay().plusMinutes(room.open());
                LocalDateTime closing = from.toLocalDate().atStartOfDay().plusMinutes(room.close());
                InputValidation.require(!from.isBefore(opening) && !to.isAfter(closing), "Период выходит за время работы помещения");
            }
            InputValidation.require(state.bookings.stream().noneMatch(b -> b.roomId().equals(roomId) && from.isBefore(b.end()) && to.isAfter(b.start())), "Помещение уже забронировано на этот период");
            Booking booking = new Booking(UUID.randomUUID().toString(), roomId, from, to, user);
            state.bookings.add(booking); return booking;
        });
    }

    public void cancel(String id, String owner) throws IOException {
        String user = InputValidation.label(owner);
        repository.transaction(true, state -> {
            Booking booking = state.bookings.stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Бронирование не найдено"));
            InputValidation.require(booking.owner().equals(user), "Имя владельца не совпадает");
            state.bookings.remove(booking); return null;
        });
    }

    public List<Room> rooms() throws IOException { return repository.transaction(false, state -> List.copyOf(state.rooms)); }

    public List<Booking> bookings(String roomId) throws IOException {
        return repository.transaction(false, state -> {
            if (roomId != null) findRoom(state, roomId);
            return state.bookings.stream().filter(b -> roomId == null || b.roomId().equals(roomId)).sorted(Comparator.comparing(Booking::start)).toList();
        });
    }

    private Room findRoom(BookingState state, String id) {
        return state.rooms.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Помещение не найдено. Получите ID командой rooms."));
    }
}
