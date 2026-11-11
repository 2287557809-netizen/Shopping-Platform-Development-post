package hu.unimiskolc.shopstream.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationStoreTest {

    @Test
    void returnsNewestNotificationsFirst() {
        NotificationStore store = new NotificationStore();
        store.addIfNew("a", "One", 1L, "first");
        store.addIfNew("b", "Two", 2L, "second");

        assertThat(store.latest(10))
                .extracting(Notification::message)
                .containsExactly("second", "first");
    }

    @Test
    void filtersByOrderId() {
        NotificationStore store = new NotificationStore();
        store.addIfNew("a", "One", 1L, "first");
        store.addIfNew("b", "Two", 2L, "second");

        assertThat(store.byOrder(2L))
                .extracting(Notification::message)
                .containsExactly("second");
    }

    @Test
    void capsTheListAtTwoHundredEntries() {
        NotificationStore store = new NotificationStore();
        for (int i = 0; i < 250; i++) {
            store.addIfNew("id-" + i, "Type", (long) i, "message");
        }

        assertThat(store.latest(500)).hasSize(200);
    }
}
