package kr.local.listenloop;
import java.util.Locale;
import java.util.regex.Pattern;

/** Literal phrase matching; never treats user input as a regular expression. */
public final class SentenceSearch {
    private SentenceSearch() {}
    public static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace('’', '\'').replaceAll("[\\s\\u00a0]+", " ").trim();
    }
    public static boolean matches(String text,String query) {
        String needle=normalize(query);
        if(needle.isEmpty())return false;
        return Pattern.compile("(?<![a-z0-9'])"+Pattern.quote(needle)+"(?![a-z0-9'])").matcher(normalize(text)).find();
    }
}
