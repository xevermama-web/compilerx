package compilerx.runtime;

import compilerx.lang.Diagnostic;
import compilerx.lang.Token;
import compilerx.lang.Type;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public record Value(Type type, Object raw) {
    public static final Value VOID = new Value(Type.VOID, null);
    public static Value integer(long value) { return new Value(Type.INT, (long) (int) value); }
    public static Value bool(boolean value) { return new Value(Type.BOOL, value ? 1L : 0L); }
    public long integer() { return ((Number) raw).longValue(); }
    public double number() { return ((Number) raw).doubleValue(); }
    public boolean truth() { return number() != 0.0; }

    public Value convert(Type target, Token token) {
        if (target == Type.VOID && type == Type.VOID) return VOID;
        if (target == Type.STRING && type == Type.STRING) return this;
        if (!(type.numeric() && target.numeric())) throw error(token, "Invalid conversion from " + type.label() + " to " + target.label() + ".");
        if (target == Type.BOOL) return bool(truth());
        if (target.floating()) {
            double n = target == Type.FLOAT ? (double) (float) number() : number();
            if (!Double.isFinite(n)) throw error(token, "Floating-point overflow or invalid mathematical result.");
            return new Value(target, n);
        }
        if (type.floating()) {
            double n = number();
            double low = target == Type.LONG ? -0x1.0p63 : target == Type.INT ? Integer.MIN_VALUE : target == Type.SHORT ? Short.MIN_VALUE : 0;
            double high = target == Type.LONG ? 0x1.0p63 : target == Type.INT ? 2147483648.0 : target == Type.SHORT ? 32768.0 : 65536.0;
            if (!Double.isFinite(n) || n < low || n >= high) throw error(token, "Floating-point value is outside the destination integer range.");
        }
        long value = integer();
        value = switch (target) {
            case INT -> (int) value; case SHORT -> (short) value; case CHAR -> (char) value;
            default -> value;
        };
        return new Value(target, value);
    }

    public String display() {
        if (type == Type.STRING) return (String) raw;
        if (type == Type.CHAR) return String.valueOf((char) integer());
        if (type == Type.VOID) return "";
        if (type.integral()) return Long.toString(integer());
        double value = number();
        if (value == 0.0) return Double.doubleToRawLongBits(value) < 0 ? "-0" : "0";
        BigDecimal rounded = BigDecimal.valueOf(value).round(new MathContext(6, RoundingMode.HALF_UP)).stripTrailingZeros();
        int exponent = rounded.precision() - rounded.scale() - 1;
        if (exponent >= -4 && exponent < 6) return rounded.toPlainString();
        String mantissa = rounded.movePointLeft(exponent).stripTrailingZeros().toPlainString();
        return mantissa + "e" + (exponent >= 0 ? "+" : "-") + String.format(java.util.Locale.ROOT, "%02d", Math.abs(exponent));
    }

    public static Diagnostic error(Token token, String message) { return new Diagnostic("Runtime", token, message); }
}
