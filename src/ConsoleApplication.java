import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class ConsoleApplication {
    private ConsoleApplication() {}
    static String time(int minutes) { return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60); }
    static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>(); StringBuilder token = new StringBuilder();
        boolean quote = false, active = false;
        for (char c : line.toCharArray()) {
            if (c == '"') { quote = !quote; active = true; }
            else if (Character.isWhitespace(c) && !quote) { if (active) { tokens.add(token.toString()); token.setLength(0); active = false; } }
            else { token.append(c); active = true; }
        }
        InputValidation.require(!quote, "Закройте двойные кавычки");
        if (active) tokens.add(token.toString()); return tokens;
    }
    static final String HELP = """
            Команды (названия с пробелами заключайте в двойные кавычки):
              room-add "Большой зал" 09:00 18:00 20
              rooms
              book ID_ПОМЕЩЕНИЯ 2026-11-01T10:00 2026-11-01T11:00 "Иван"
              bookings [ID_ПОМЕЩЕНИЯ]
              cancel ID_БРОНИ "Иван"
              help
              exit
            Время работы одинаковое каждый день. 00:00 24:00 = круглосуточно.
            Окончание не включается: соседние брони разрешены. Даты — местное время.
            """;
    static void execute(BookingService service, List<String> args) throws IOException {
        if (args.isEmpty()) return;
        switch (args.get(0)) {
            case "help" -> { arity(args, 1); System.out.print(HELP); }
            case "rooms" -> {
                arity(args, 1); List<Room> rooms = service.rooms();
                if (rooms.isEmpty()) System.out.println("Помещений пока нет. Добавьте: room-add \"Название\" 09:00 18:00 10");
                for (Room r : rooms) System.out.printf("%s | %s | %s–%s | мест: %d%n", r.id(), r.name(), time(r.open()), time(r.close()), r.capacity());
            }
            case "room-add" -> { arity(args, 5); Room r = service.addRoom(args.get(1), args.get(2), args.get(3), args.get(4)); System.out.println("Помещение добавлено. ID: " + r.id()); }
            case "book" -> { arity(args, 5); Booking b = service.book(args.get(1), args.get(2), args.get(3), args.get(4)); System.out.println("Бронь создана. ID: " + b.id()); }
            case "bookings" -> {
                InputValidation.require(args.size() == 1 || args.size() == 2, "Использование: bookings [ID_ПОМЕЩЕНИЯ]");
                List<Booking> bookings = service.bookings(args.size() == 2 ? args.get(1) : null);
                if (bookings.isEmpty()) System.out.println("Бронирований нет.");
                for (Booking b : bookings) System.out.printf("%s | помещение: %s | %s — %s | %s%n", b.id(), b.roomId(), b.start(), b.end(), b.owner());
            }
            case "cancel" -> { arity(args, 3); service.cancel(args.get(1), args.get(2)); System.out.println("Бронь отменена."); }
            default -> throw new IllegalArgumentException("Неизвестная команда. Введите help.");
        }
    }
    static void arity(List<String> args, int expected) { InputValidation.require(args.size() == expected, "Неверное число аргументов. Справка: help"); }
    public static void start(String[] args) {
        List<String> options = new ArrayList<>(List.of(args)); Path directory = Path.of("data");
        try {
            if (!options.isEmpty() && options.get(0).equals("--data")) {
                InputValidation.require(options.size() >= 2, "Укажите каталог после --data"); directory = Path.of(options.remove(1)); options.remove(0);
            }
            BookingService service = new BookingService(directory);
            if (!options.isEmpty()) {
                InputValidation.require(options.remove(0).equals("--exec") && !options.isEmpty(), "Запуск: [--data КАТАЛОГ] [--exec КОМАНДА аргументы]");
                execute(service, options); return;
            }
            System.out.println("Система бронирования помещений. Данные: " + directory.toAbsolutePath());
            System.out.print(HELP);
            Scanner scanner = new Scanner(System.in, java.nio.charset.StandardCharsets.UTF_8);
            while (true) {
                System.out.print("booking> ");
                if (!scanner.hasNextLine()) break;
                try {
                    List<String> command = tokenize(scanner.nextLine());
                    if (command.equals(List.of("exit"))) break;
                    execute(service, command);
                } catch (IllegalArgumentException | IOException ex) { System.out.println("Ошибка: " + ex.getMessage()); }
            }
        } catch (IllegalArgumentException | IOException ex) { System.err.println("Ошибка: " + ex.getMessage()); System.exit(1); }
    }
}

