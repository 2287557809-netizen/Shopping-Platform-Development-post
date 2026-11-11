package hu.unimiskolc.shopstream.notification;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationStore store;

    public NotificationController(NotificationStore store) {
        this.store = store;
    }

    @GetMapping
    public List<Notification> latest(@RequestParam(defaultValue = "50") int limit) {
        return store.latest(limit);
    }

    @GetMapping("/{orderId}")
    public List<Notification> byOrder(@PathVariable Long orderId) {
        return store.byOrder(orderId);
    }
}
