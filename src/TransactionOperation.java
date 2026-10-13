import java.io.IOException;

@FunctionalInterface
interface TransactionOperation<T> {
    T run(BookingState state) throws IOException;
}
