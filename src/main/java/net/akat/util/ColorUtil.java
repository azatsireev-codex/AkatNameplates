package net.akat.util;

import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:(#[a-fA-F0-9]{6}):(#[a-fA-F0-9]{6})>(.*?)</gradient>", Pattern.DOTALL);

    public static String colorize(String text) {
        if (text == null) return "";

        Matcher gradientMatcher = GRADIENT_PATTERN.matcher(text);
        while (gradientMatcher.find()) {
            String full = gradientMatcher.group(0);
            String start = gradientMatcher.group(1);
            String end = gradientMatcher.group(2);
            String content = gradientMatcher.group(3);
            text = text.replace(full, applyGradient(start, end, content));
        }

        Matcher matcher = HEX_PATTERN.matcher(text);
        while (matcher.find()) {
            String hex = matcher.group();
            ChatColor color = ChatColor.of(hex);
            text = text.replace(hex, color.toString());
        }

        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static String applyGradient(String startHex, String endHex, String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        int startRed = Integer.parseInt(startHex.substring(1, 3), 16);
        int startGreen = Integer.parseInt(startHex.substring(3, 5), 16);
        int startBlue = Integer.parseInt(startHex.substring(5, 7), 16);

        int endRed = Integer.parseInt(endHex.substring(1, 3), 16);
        int endGreen = Integer.parseInt(endHex.substring(3, 5), 16);
        int endBlue = Integer.parseInt(endHex.substring(5, 7), 16);

        StringBuilder sb = new StringBuilder();
        int length = content.length();
        for (int i = 0; i < length; i++) {
            double ratio = length == 1 ? 0D : (double) i / (double) (length - 1);
            int red = (int) Math.round(startRed + (endRed - startRed) * ratio);
            int green = (int) Math.round(startGreen + (endGreen - startGreen) * ratio);
            int blue = (int) Math.round(startBlue + (endBlue - startBlue) * ratio);
            String hex = String.format("#%02X%02X%02X", red, green, blue);
            sb.append(ChatColor.of(hex)).append(content.charAt(i));
        }
        return sb.toString();
    }
}
