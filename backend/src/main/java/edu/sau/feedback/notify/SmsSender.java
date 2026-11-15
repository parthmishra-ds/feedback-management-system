package edu.sau.feedback.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/** Sends SMS through Twilio's REST API using only the JDK HTTP client (no extra library). When disabled it only logs. */
@Component
public class SmsSender {
    private static final Logger log = LoggerFactory.getLogger(SmsSender.class);

    private final boolean enabled;
    private final String sid, token, from;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public SmsSender(@Value("${app.notify.sms.enabled:false}") boolean enabled,
                     @Value("${app.notify.sms.twilio.account-sid:}") String sid,
                     @Value("${app.notify.sms.twilio.auth-token:}") String token,
                     @Value("${app.notify.sms.twilio.from:}") String from) {
        this.enabled = enabled;
        this.sid = sid;
        this.token = token;
        this.from = from;
    }

    public void send(String to, String text) throws Exception {
        if (!enabled || sid.isBlank() || token.isBlank() || from.isBlank()) {
            log.info("[SMS not sent: {}] to={} text=\"{}\"", enabled ? "TWILIO_* is not set" : "NOTIFY_SMS is off", to, text);
            return;
        }
        String form = "To=" + enc(to) + "&From=" + enc(from) + "&Body=" + enc(text);
        String auth = Base64.getEncoder().encodeToString((sid + ":" + token).getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + sid + "/Messages.json"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) throw new IllegalStateException("Twilio answered HTTP " + res.statusCode());
        log.info("[SMS sent] to={}", to);
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
