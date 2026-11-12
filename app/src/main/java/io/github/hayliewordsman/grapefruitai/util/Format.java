package io.github.hayliewordsman.grapefruitai.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

import io.github.hayliewordsman.grapefruitai.or.OrModel;

/** Text for prices, token counts and sizes. All prices are US dollars and say so. */
public final class Format {
    public static final String NOT_LISTED = "not listed";

    private Format() {
    }

    /** "$0.15" / "$0.0004" / "Free" / "variable" / "not listed" from a USD amount. */
    public static String usd(BigDecimal v) {
        if (v == null) return NOT_LISTED;
        if (v.signum() < 0) return "variable";
        if (v.signum() == 0) return "Free";
        BigDecimal abs = v.stripTrailingZeros();
        String s;
        if (abs.compareTo(BigDecimal.valueOf(100)) >= 0) s = abs.setScale(0, RoundingMode.HALF_UP).toPlainString();
        else if (abs.compareTo(BigDecimal.ONE) >= 0) s = abs.setScale(2, RoundingMode.HALF_UP).toPlainString();
        else if (abs.compareTo(new BigDecimal("0.01")) >= 0) s = trim(abs.setScale(3, RoundingMode.HALF_UP));
        else s = trim(abs.round(new java.math.MathContext(2, RoundingMode.HALF_UP)));
        return "$" + s;
    }

    private static String trim(BigDecimal b) {
        String s = b.stripTrailingZeros().toPlainString();
        int dot = s.indexOf('.');
        // Cents keep two places ($1.50, not $1.5).
        if (dot >= 0 && s.length() - dot - 1 == 1) s = s + "0";
        return s;
    }

    /** "$3.00 / M tokens" from a per-token price. */
    public static String perMillion(String perToken) {
        BigDecimal m = OrModel.perMillion(perToken);
        if (m == null) return NOT_LISTED;
        String u = usd(m);
        return m.signum() > 0 ? u + " / M tokens" : u;
    }

    /** "$0.04 / image" from a per-unit price. */
    public static String perUnit(String price, String unit) {
        BigDecimal v = OrModel.value(price);
        if (v == null) return NOT_LISTED;
        String u = usd(v);
        return v.signum() > 0 ? u + " / " + unit : u;
    }

    public static String tokens(long n) {
        if (n <= 0) return "unknown";
        if (n >= 1000000) {
            return BigDecimal.valueOf(n).divide(BigDecimal.valueOf(1000000), 1, RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString() + "M";
        }
        if (n >= 1000) return (n + 500) / 1000 + "K";
        return String.valueOf(n);
    }

    public static String count(long n) {
        return String.format(Locale.US, "%,d", n);
    }

    public static String cost(double usd) {
        if (usd <= 0) return "$0";
        return usd(BigDecimal.valueOf(usd));
    }

    public static String bytes(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return (n / 1024) + " KB";
        return String.format(Locale.US, "%.1f MB", n / 1048576.0);
    }

    public static String join(List<String> items, String sep) {
        StringBuilder b = new StringBuilder();
        for (String s : items) {
            if (b.length() > 0) b.append(sep);
            b.append(s);
        }
        return b.toString();
    }

    /** One line for a model list: provider, context, modalities, tools. */
    public static String modelSummary(OrModel m) {
        StringBuilder b = new StringBuilder();
        b.append(m.provider.length() > 0 ? m.provider : "unknown provider");
        b.append(" · ").append(tokens(m.contextLength)).append(" context");
        b.append(" · ").append(join(m.input, "+")).append(" → ").append(join(m.output, "+"));
        if (m.supportsTools()) b.append(" · tools");
        return b.toString();
    }

    /** Prices for a list row, labelled USD. */
    public static String modelPrices(OrModel m) {
        if (m.isFree()) return "Free (USD 0)";
        StringBuilder b = new StringBuilder("USD in ");
        b.append(perMillion(m.pricePrompt)).append(" · out ").append(perMillion(m.priceCompletion));
        if (m.isImage()) {
            if (m.priceImageOutput != null) b.append(" · images ").append(perMillion(m.priceImageOutput));
        }
        return b.toString();
    }

    /** Everything OpenRouter lists for a model, for its details sheet. */
    public static String modelDetails(OrModel m) {
        StringBuilder b = new StringBuilder();
        b.append("ID: ").append(m.id).append('\n');
        b.append("Provider: ").append(m.provider).append('\n');
        b.append("Context window: ").append(m.contextLength > 0 ? count(m.contextLength) + " tokens" : "unknown").append('\n');
        if (m.maxCompletion > 0) b.append("Max output: ").append(count(m.maxCompletion)).append(" tokens\n");
        b.append("Input: ").append(m.input.isEmpty() ? "unknown" : join(m.input, ", ")).append('\n');
        b.append("Output: ").append(m.output.isEmpty() ? "unknown" : join(m.output, ", ")).append('\n');
        b.append("Tool calling: ").append(m.parameters.isEmpty() ? "unknown" : m.supportsTools() ? "yes" : "no").append('\n');
        b.append("\nPricing (US dollars, as listed by OpenRouter)\n");
        b.append("Input: ").append(perMillion(m.pricePrompt)).append('\n');
        b.append("Output: ").append(perMillion(m.priceCompletion)).append('\n');
        if (m.priceReasoning != null) b.append("Internal reasoning: ").append(perMillion(m.priceReasoning)).append('\n');
        if (m.priceCacheRead != null) b.append("Cached input read: ").append(perMillion(m.priceCacheRead)).append('\n');
        if (m.priceCacheWrite != null) b.append("Cache write: ").append(perMillion(m.priceCacheWrite)).append('\n');
        if (m.priceImage != null) b.append("Input images: ").append(perUnit(m.priceImage, "unit (pricing.image)")).append('\n');
        if (m.priceImageOutput != null) b.append("Output image tokens: ").append(perMillion(m.priceImageOutput)).append(" (pricing.image_output)\n");
        if (m.priceImageToken != null) b.append("Image tokens: ").append(perMillion(m.priceImageToken)).append(" (pricing.image_token)\n");
        if (m.priceAudio != null) b.append("Audio input: ").append(perMillion(m.priceAudio)).append('\n');
        if (m.priceRequest != null) b.append("Per request (extra): ").append(perUnit(m.priceRequest, "request")).append('\n');
        if (m.priceWebSearch != null) b.append("Web search (extra): ").append(perUnit(m.priceWebSearch, "search")).append('\n');
        if (m.discount > 0) b.append("Discount: ").append(Math.round(m.discount * 100)).append("% off listed prices\n");
        if (m.hasPriceOverrides) b.append("Some prices change with prompt length or time of day (OpenRouter overrides).\n");
        if (m.isImage()) {
            b.append(m.priceImageOutput == null ? "Image output price: not listed by OpenRouter.\n" : "");
            b.append("The cost of an image depends on its size and quality; the actual charge OpenRouter reports is shown after each generation.\n");
        }
        if (m.description.length() > 0) b.append('\n').append(m.description);
        return b.toString().trim();
    }
}
