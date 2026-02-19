package net.akat.util;

import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");

    public static String colorize(String text) {
        if (text == null) return "";

        Matcher matcher = HEX_PATTERN.matcher(text);
        while (matcher.find()) {
            String hex = matcher.group();
            ChatColor color = ChatColor.of(hex);
            text = text.replace(hex, color.toString());
        }

        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
