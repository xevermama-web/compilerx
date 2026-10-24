package compilerx.runtime;

import compilerx.lang.Token;
import compilerx.lang.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Formats {
    private static final Pattern PRINT = Pattern.compile("%([-+ 0#]*)([0-9]*)(?:\\.([0-9]+))?(ll|l)?([diufFeEgGcs])");
    private static final Pattern SCAN = Pattern.compile("%(ll|l|h)?([dfc])");
    record Part(String literal, String flags, String width, String precision, String length, char kind) {}

    static List<Part> parse(String format, boolean input, Token token) {
        List<Part> parts = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < format.length();) {
            char c = format.charAt(i);
            if (c != '%') { literal.append(c); i++; continue; }
            if (i + 1 < format.length() && format.charAt(i + 1) == '%') { literal.append('%'); i += 2; continue; }
            if (literal.length() > 0) { parts.add(new Part(literal.toString(), "", "", "", "", '\0')); literal.setLength(0); }
            Matcher matcher = (input ? SCAN : PRINT).matcher(format);
            matcher.region(i, format.length());
            if (!matcher.lookingAt()) throw Value.error(token, "Unsupported " + (input ? "scanf" : "printf") + " format near '" + format.substring(i, Math.min(i + 10, format.length())) + "'.");
            if (input) {
                String length = empty(matcher.group(1));
                char kind = matcher.group(2).charAt(0);
                if (kind == 'c' && !length.isEmpty() || kind == 'f' && !(length.isEmpty() || length.equals("l"))) throw Value.error(token, "Unsupported scanf length modifier.");
                parts.add(new Part(null, "", "", "", length, kind));
            } else {
                String width = empty(matcher.group(2)), precision = empty(matcher.group(3));
                checkSize(width, token); checkSize(precision, token);
                char kind = matcher.group(5).charAt(0);
                if (kind == 'u') throw Value.error(token, "Unsigned formatting is outside this signed-numeric subset.");
                String length = empty(matcher.group(4));
                if (!length.isEmpty() && !(kind == 'd' || kind == 'i' || "fFeEgG".indexOf(kind) >= 0 && length.equals("l"))) throw Value.error(token, "Unsupported printf length modifier.");
                parts.add(new Part(null, matcher.group(1), width, precision, length, kind));
            }
            i = matcher.end();
        }
        if (literal.length() > 0) parts.add(new Part(literal.toString(), "", "", "", "", '\0'));
        return parts;
    }

    static Type inputType(Part part) {
        if (part.kind == 'c') return Type.CHAR;
        if (part.kind == 'f') return part.length.equals("l") ? Type.DOUBLE : Type.FLOAT;
        return part.length.equals("h") ? Type.SHORT : part.length.isEmpty() ? Type.INT : Type.LONG;
    }

    static String print(Part part, Value value, Token token) {
        char kind = part.kind == 'i' ? 'd' : part.kind == 'F' ? 'f' : part.kind;
        Object argument;
        if (kind == 's') {
            if (value.type() != Type.STRING) throw Value.error(token, "%s requires a string.");
            argument = value.raw();
        } else if (kind == 'c') {
            if (!value.type().integral()) throw Value.error(token, "%c requires an integral value.");
            argument = (char) value.integer();
        } else if (kind == 'd') {
            if (!value.type().integral()) throw Value.error(token, "%d requires an integral value.");
            argument = value.integer();
        } else {
            if (!value.type().numeric()) throw Value.error(token, "Floating-point formatting requires a numeric value.");
            argument = value.number();
        }
        String specification = "%" + part.flags + part.width + (part.precision.isEmpty() ? "" : "." + part.precision) + kind;
        try { return String.format(Locale.ROOT, specification, argument); }
        catch (java.util.IllegalFormatException exception) { throw Value.error(token, "Unsupported format combination '" + specification + "'."); }
    }

    private static void checkSize(String text, Token token) {
        if (text.length() > 4 || !text.isEmpty() && Integer.parseInt(text) > 1000) throw Value.error(token, "Format width and precision must be at most 1,000.");
    }
    private static String empty(String value) { return value == null ? "" : value; }
}
