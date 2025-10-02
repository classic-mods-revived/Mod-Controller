package net.cmr.modcontroller.locator;

import com.google.gson.Gson;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Standalone process: shows a Swing window and polls a JSON file for progress data.
 * Run from the locator via ProcessBuilder to avoid LWJGL/Swing conflicts.
 */
public class ProgressUiHelper {

    private static final Gson GSON = new Gson();

    private static class ProgressPayload {
        String phase;
        String message;
        Integer progress;
        Boolean done;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) return;
        Path progressFile = Path.of(args[0]);

        System.setProperty("java.awt.headless", "false");

        JFrame frame = new JFrame("Mod Controller");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setSize(520, 220);
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        panel.setBackground(new Color(45, 45, 48));

        JLabel title = new JLabel("Mod Controller");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setForeground(new Color(255, 170, 0));
        panel.add(title);
        panel.add(Box.createRigidArea(new Dimension(0, 15)));

        JLabel status = new JLabel("Initializing...");
        status.setAlignmentX(Component.CENTER_ALIGNMENT);
        status.setForeground(Color.WHITE);
        panel.add(status);
        panel.add(Box.createRigidArea(new Dimension(0, 10)));

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        bar.setPreferredSize(new Dimension(440, 25));
        bar.setMaximumSize(new Dimension(440, 25));
        panel.add(bar);
        panel.add(Box.createRigidArea(new Dimension(0, 10)));

        JLabel detail = new JLabel("Starting...");
        detail.setAlignmentX(Component.CENTER_ALIGNMENT);
        detail.setForeground(new Color(200, 200, 200));
        panel.add(detail);

        frame.add(panel);
        frame.setVisible(true);

        while (true) {
            if (!Files.exists(progressFile)) {
                Thread.sleep(100);
                continue;
            }
            try {
                String json = Files.readString(progressFile);
                ProgressPayload p = GSON.fromJson(json, ProgressPayload.class);
                if (p == null) {
                    Thread.sleep(100);
                    continue;
                }
                final String phase = p.phase != null ? p.phase : "Preparing";
                final String message = p.message != null ? p.message : "";
                final int prog = p.progress != null ? Math.max(0, Math.min(100, p.progress)) : 0;
                final boolean done = p.done != null && p.done;

                SwingUtilities.invokeLater(() -> {
                    status.setText(phase);
                    bar.setValue(prog);
                    bar.setString(prog + "%");
                    detail.setText(message);
                });

                if (done) break;

            } catch (IOException ignored) {
                // file may be in write; retry
            }
            Thread.sleep(100);
        }

        SwingUtilities.invokeLater(() -> {
            frame.setVisible(false);
            frame.dispose();
        });
    }
}
