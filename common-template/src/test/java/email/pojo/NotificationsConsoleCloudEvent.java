package email.pojo;

import com.redhat.cloud.event.core.v1.Notification;
import com.redhat.cloud.event.core.v1.Recipients;
import com.redhat.cloud.event.parser.ConsoleCloudEvent;
import java.util.Optional;

public class NotificationsConsoleCloudEvent extends ConsoleCloudEvent {

    public Optional<Recipients> getRecipients() {
        return this.getData(Notification.class).map(Notification::getNotificationRecipients);
    }
}
