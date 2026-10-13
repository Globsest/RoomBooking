import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Хранилище и взаимодействие процессов")
class BookingRepositoryTest {
    @TempDir Path directory;

    @Test @DisplayName("Повреждённый файл отклоняется и остаётся без изменений")
    void corruptedStorage() throws Exception {
        byte[] invalid = {1, 2, 3};
        Path file = directory.resolve("state.bin");
        Files.write(file, invalid);
        assertThrows(IOException.class,
                () -> new BookingService(directory).addRoom("X", "09:00", "18:00", "1"));
        assertArrayEquals(invalid, Files.readAllBytes(file));
    }

    @RepeatedTest(value = 3, name = "Восемь конкурирующих процессов: раунд {currentRepetition}")
    void concurrentBooking() throws Exception {
        var service = new BookingService(directory);
        var room = service.addRoom("Race", "00:00", "24:00", "2");
        List<Process> processes = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                processes.add(startProcess(directory.resolve("race-" + i + ".log"), "book", room.id(),
                        "2026-11-05T10:00", "2026-11-05T11:00", "User" + i));
            }
            int successful = 0;
            for (int i = 0; i < processes.size(); i++) {
                Process process = processes.get(i);
                assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Процесс завис");
                String output = Files.readString(directory.resolve("race-" + i + ".log"));
                if (process.exitValue() == 0) successful++;
                else {
                    assertEquals(1, process.exitValue(), output);
                    assertTrue(output.contains("уже забронировано"), output);
                }
            }
            assertEquals(1, successful, "Успешной должна быть ровно одна бронь");
            assertEquals(1, service.bookings(null).size());
        } finally {
            for (Process process : processes) {
                if (process.isAlive()) process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    @Test @DisplayName("Отдельная JVM читает ранее сохранённые данные")
    void restartInAnotherJvm() throws Exception {
        new BookingService(directory).addRoom("Большой зал", "09:00", "18:00", "20");
        Path log = directory.resolve("restart.log");
        Process process = startProcess(log, "rooms");
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(log));
            assertTrue(Files.readString(log).contains("Большой зал"));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
    }

    private Process startProcess(Path log, String... command) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String runtime = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        List<String> arguments = getStrings(runtime);
        arguments.addAll(List.of(command));
        return new ProcessBuilder(arguments).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    }

    private List<String> getStrings(String runtime) throws URISyntaxException {
        String classpath = Path.of(RoomBooking.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        List<String> arguments = new ArrayList<>(List.of(runtime, "-Xmx64m", "-XX:ActiveProcessorCount=2",
                "-Dfile.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8",
                "-cp", classpath, "RoomBooking", "--data", directory.toString(), "--exec"));
        return arguments;
    }
}
