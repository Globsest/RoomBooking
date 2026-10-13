import java.time.LocalDateTime;

public record Booking(String id, String roomId, LocalDateTime start, LocalDateTime end, String owner) {}
