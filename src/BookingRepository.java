import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.LocalDateTime;

public final class BookingRepository {
    private final Path directory;

    public BookingRepository(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }

    <T> T transaction(boolean write, TransactionOperation<T> operation) throws IOException {
        Files.createDirectories(directory);
        try (FileChannel channel = FileChannel.open(directory.resolve("state.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock = null;
            long deadline = System.nanoTime() + 10_000_000_000L;
            while (lock == null) {
                try { lock = channel.tryLock(); } catch (OverlappingFileLockException ignored) { }
                if (lock == null) {
                    if (System.nanoTime() > deadline) throw new IOException("Хранилище занято. Повторите запрос.");
                    try { Thread.sleep(25); } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt(); throw new IOException("Операция прервана", ex);
                    }
                }
            }
            try {
                BookingState state = read();
                T result = operation.run(state);
                if (write) save(state);
                return result;
            } finally { lock.release(); }
        }
    }

    private BookingState read() throws IOException {
        Path path = directory.resolve("state.bin");
        BookingState state = new BookingState();
        if (!Files.exists(path)) return state;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            if (!in.readUTF().equals("ROOM_BOOKING_1")) throw new IOException("Неверный формат хранилища");
            int rooms = count(in);
            for (int i = 0; i < rooms; i++) state.rooms.add(new Room(in.readUTF(), in.readUTF(), in.readInt(), in.readInt(), in.readInt()));
            int bookings = count(in);
            for (int i = 0; i < bookings; i++) state.bookings.add(new Booking(in.readUTF(), in.readUTF(), LocalDateTime.parse(in.readUTF()), LocalDateTime.parse(in.readUTF()), in.readUTF()));
            if (in.read() != -1) throw new IOException("Лишние данные в хранилище");
        } catch (IOException | RuntimeException ex) {
            throw new IOException("Хранилище повреждено или недоступно; оно не было перезаписано. " + ex.getMessage(), ex);
        }
        return state;
    }

    private int count(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > 1_000_000) throw new IOException("Некорректное число записей");
        return count;
    }

    private void save(BookingState state) throws IOException {
        Path temp = Files.createTempFile(directory, "state-", ".tmp");
        try {
            try (FileOutputStream file = new FileOutputStream(temp.toFile());
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(file))) {
                out.writeUTF("ROOM_BOOKING_1"); out.writeInt(state.rooms.size());
                for (Room r : state.rooms) {
                    out.writeUTF(r.id()); out.writeUTF(r.name()); out.writeInt(r.open()); out.writeInt(r.close()); out.writeInt(r.capacity());
                }
                out.writeInt(state.bookings.size());
                for (Booking b : state.bookings) {
                    out.writeUTF(b.id()); out.writeUTF(b.roomId()); out.writeUTF(b.start().toString()); out.writeUTF(b.end().toString()); out.writeUTF(b.owner());
                }
                out.flush(); file.getFD().sync();
            }
            Files.move(temp, directory.resolve("state.bin"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
