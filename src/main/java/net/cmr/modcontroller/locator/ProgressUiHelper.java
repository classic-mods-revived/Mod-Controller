package net.cmr.modcontroller.locator;

import com.google.gson.Gson;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Standalone process: shows a Swing window and polls a JSON file for progress data.
 * Also writes user decisions to a command file when prompted.
 */
public class ProgressUiHelper {

    private static final Gson GSON = new Gson();

    private static class ProgressPayload {
        String phase;
        String message;
        Integer progress;
        Boolean done;
        String mode; // null, "prompt", or "consent"
    }

    private static class CommandPayload {
        String action; // "continue" or "exit"
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) return;
        Path progressFile = Path.of(args[0]);
        Path commandFile = Path.of(args[1]);

        System.setProperty("java.awt.headless", "false");

        JFrame frame = new JFrame("Mod Controller");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setSize(520, 260);
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
        panel.add(Box.createRigidArea(new Dimension(0, 10)));

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        JButton continueBtn = new JButton("Continue Anyway");
        JButton exitBtn = new JButton("Exit Game");
        JButton acceptBtn = new JButton("I Consent");
        JButton declineBtn = new JButton("Exit");
        buttons.add(continueBtn);
        buttons.add(exitBtn);
        buttons.add(acceptBtn);
        buttons.add(declineBtn);
        buttons.setVisible(false);
        // default hidden pairs
        continueBtn.setVisible(false);
        exitBtn.setVisible(false);
        acceptBtn.setVisible(false);
        declineBtn.setVisible(false);
        panel.add(buttons);

        // button actions
        continueBtn.addActionListener(e -> writeDecision(commandFile, "continue"));
        exitBtn.addActionListener(e -> writeDecision(commandFile, "exit"));
        acceptBtn.addActionListener(e -> writeDecision(commandFile, "accept"));
        declineBtn.addActionListener(e -> writeDecision(commandFile, "exit"));

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
                final boolean prompt = p.mode != null && "prompt".equalsIgnoreCase(p.mode);
                final boolean consent = p.mode != null && "consent".equalsIgnoreCase(p.mode);

                SwingUtilities.invokeLater(() -> {
                    status.setText(phase);
                    bar.setValue(prog);
                    bar.setString(prog + "%");
                    detail.setText(message);

                    // toggle button sets
                    buttons.setVisible(prompt || consent);
                    continueBtn.setVisible(prompt);
                    exitBtn.setVisible(prompt);
                    acceptBtn.setVisible(consent);
                    declineBtn.setVisible(consent);
                });

                if (done) break;

            } catch (Exception ignored) {}
            Thread.sleep(100);
        }

        SwingUtilities.invokeLater(() -> {
            frame.setVisible(false);
            frame.dispose();
        });
    }

    private static void writeDecision(Path commandFile, String action) {
        try {
            CommandPayload cmd = new CommandPayload();
            cmd.action = action;
            Files.writeString(commandFile, GSON.toJson(cmd));
        } catch (Exception e) {
            // ignore
        }
    }
}
