package com.ahmedyahia.ttspooltracker;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NotificationEngine {
    private static final String PREFS = "sentinel_notifications";
    private static final String SEEN = "seen_events";
    private static final String CHANNEL_ID = "sentinel_alerts";
    private static final String CHANNEL_NAME = "Sentinel Alerts";

    private NotificationEngine() {}

    public static void initialize(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("TTS Pool Tracker operational alerts");
                channel.enableVibration(true);
                nm.createNotificationChannel(channel);
            }
        }
    }

    public static void evaluate(Context context, JSONObject report, boolean notifySystem) {
        if (report == null) return;
        initialize(context);

        List<Alert> alerts = buildAlerts(report);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> seen = new HashSet<>(prefs.getStringSet(SEEN, new HashSet<>()));
        SharedPreferences.Editor editor = prefs.edit();

        int emitted = 0;
        for (Alert alert : alerts) {
            if (alert.key == null || alert.key.isEmpty() || seen.contains(alert.key)) continue;
            seen.add(alert.key);
            if (notifySystem) {
                post(context, alert);
                emitted++;
            }
        }

        // Keep the cache bounded.
        if (seen.size() > 250) {
            List<String> all = new ArrayList<>(seen);
            seen.clear();
            for (int i = Math.max(0, all.size() - 150); i < all.size(); i++) seen.add(all.get(i));
        }
        editor.putStringSet(SEEN, seen).apply();
    }

    public static List<Alert> buildAlerts(JSONObject report) {
        List<Alert> out = new ArrayList<>();
        JSONObject current = report.optJSONObject("current");
        JSONObject totals = report.optJSONObject("session_totals");
        if (current == null) current = new JSONObject();
        if (totals == null) totals = new JSONObject();

        String day = report.optString("session_date", "");
        String stamp = report.optString("last_updated", "");
        if (stamp.isEmpty()) stamp = String.valueOf(System.currentTimeMillis());

        int newTickets = current.optInt("new_ticket_count", totals.optInt("new_tickets_this_run", 0));
        if (newTickets > 0) {
            out.add(new Alert("ticket_" + day + "_" + stamp + "_" + newTickets,
                    "🎫 New Tickets Detected",
                    newTickets + " new ticket(s) were detected in the latest TTS run.",
                    Alert.WARN));
        }

        int nearViolation = current.optInt("near_violation", 0);
        if (nearViolation > 0) {
            out.add(new Alert("sla_" + day + "_" + stamp + "_" + nearViolation,
                    "⏱️ SLA Alert",
                    nearViolation + " ticket(s) are at or above the 1h30m near-violation threshold.",
                    Alert.CRITICAL));
        }

        JSONArray highGroup = totals.optJSONArray("high_group_tickets");
        if (highGroup == null) highGroup = current.optJSONArray("high_group_tickets");
        if (highGroup != null && highGroup.length() > 0) {
            JSONObject top = highGroup.optJSONObject(0);
            int count = highGroup.length();
            String ticket = top == null ? "" : top.optString("ticket_id", "");
            int group = top == null ? 0 : top.optInt("group_count", 0);
            out.add(new Alert("high_group_" + day + "_" + stamp + "_" + count,
                    "🔥 High GroupCount Activity",
                    count + " ticket(s) reached GroupCount ≥ 5." +
                            (ticket.isEmpty() ? "" : " Highest: " + ticket + " (" + group + ")."),
                    Alert.CRITICAL));
        }

        JSONArray poolRepeat = totals.optJSONArray("pool_repeated_cabinet_history");
        if (poolRepeat == null) poolRepeat = current.optJSONArray("pool_repeated_cabinet_history");
        if (poolRepeat != null && poolRepeat.length() > 0) {
            JSONObject last = poolRepeat.optJSONObject(poolRepeat.length() - 1);
            String cabinet = last == null ? "" : last.optString("cabinet", "");
            int count = last == null ? 0 : last.optInt("count", 0);
            if (count >= 3) {
                out.add(new Alert("cabinet_repeat_" + day + "_" + stamp + "_" + cabinet + "_" + count,
                        "🏢 Repeated Cabinet in Pool",
                        (cabinet.isEmpty() ? "A cabinet" : cabinet) +
                                " appeared " + count + " times in the TTS pool.",
                        Alert.WARN));
            }
        }

        JSONArray intelligence = totals.optJSONArray("cabinet_intelligence");
        if (intelligence != null) {
            int reported = 0;
            for (int i = 0; i < intelligence.length() && reported < 3; i++) {
                JSONObject x = intelligence.optJSONObject(i);
                if (x == null) continue;
                int tickets = x.optInt("tickets", 0);
                int events = x.optInt("escalations", 0);
                String risk = x.optString("risk_level", "");
                if (tickets >= 5 || events >= 5 || "HIGH".equalsIgnoreCase(risk) || "CRITICAL".equalsIgnoreCase(risk)) {
                    String cabinet = x.optString("cabinet", "-");
                    out.add(new Alert("cabinet_spike_" + day + "_" + stamp + "_" + cabinet + "_" + tickets + "_" + events,
                            "🚨 Cabinet Activity Spike",
                            cabinet + " has " + tickets + " ticket(s) and " + events + " event(s) in the latest intelligence.",
                            Alert.CRITICAL));
                    reported++;
                }
            }
        }

        int repeated = current.optInt("repeated_cabinets", 0);
        if (repeated > 0) {
            out.add(new Alert("repeated_current_" + day + "_" + stamp + "_" + repeated,
                    "🔁 Repeated Cabinets",
                    repeated + " cabinet(s) are currently repeated in the pool.",
                    Alert.WARN));
        }

        int iptv = current.optInt("iptv_our_pool_count",
                totals.optInt("iptv_our_pool_count", 0));
        if (iptv > 0) {
            out.add(new Alert("iptv_" + day + "_" + stamp + "_" + iptv,
                    "📺 IPTV Our Pool",
                    iptv + " IPTV ticket(s) are currently in Our Pool.",
                    Alert.INFO));
        }

        return out;
    }

    private static void post(Context context, Alert alert) {
        if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(alert.title)
                .setContentText(alert.body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(alert.body))
                .setPriority(alert.level == Alert.CRITICAL
                        ? NotificationCompat.PRIORITY_MAX : NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS);

        android.content.Intent tap = new android.content.Intent(context, MainActivity.class);
        tap.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
        tap.putExtra("sentinel_alert", alert.title);
        PendingIntent focusedPending = PendingIntent.getActivity(context, alert.key.hashCode(), tap,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        builder.setContentIntent(focusedPending);
        NotificationManagerCompat.from(context).notify(alert.key.hashCode(), builder.build());
    }

    public static final class Alert {
        public static final int INFO = 1;
        public static final int WARN = 2;
        public static final int CRITICAL = 3;
        public final String key, title, body;
        public final int level;
        public final String focus;
        Alert(String key, String title, String body, int level) {
            this.key = key; this.title = title; this.body = body; this.level = level; this.focus = "";
        }
    }

    // Small helper kept here so the notification engine has no dependency on Activity state.
    private static final class IntentFactory {
        static void openDashboard(Context context) {
            // No-op: kept as a single place for future deep-link handling.
        }
        static PendingIntent dashboardPendingIntent(Context context) {
            android.content.Intent intent = new android.content.Intent(context, MainActivity.class);
            intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
            return PendingIntent.getActivity(context, 1001, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        }
    }
}
