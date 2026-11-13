package io.github.hayliewordsman.grapefruitai.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

import io.github.hayliewordsman.grapefruitai.or.ImagePricing;
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
        return modelPrices(m, null);
    }

    /**
     * Prices for a list row in their real units. Image models lead with their generation price
     * ("Image generation: $0.01/image"); text token prices only appear where they apply.
     */
    public static String modelPrices(OrModel m, ImagePricing pricing) {
        if (m.isImage()) {
            StringBuilder b = new StringBuilder("Image generation: ").append(imageGeneration(m, pricing));
            if (m.outputsText()) {
                b.append(" · text in ").append(perMillion(m.pricePrompt)).append(" · out ").append(perMillion(m.priceCompletion));
            } else if (isPositive(m.pricePrompt)) {
                b.append(" · prompt ").append(perMillion(m.pricePrompt));
            }
            return b.toString();
        }
        if (m.isFree()) return "Free (USD 0)";
        StringBuilder b = new StringBuilder("USD in ");
        b.append(perMillion(m.pricePrompt)).append(" · out ").append(perMillion(m.priceCompletion));
        return b.toString();
    }

    /** "$0.01/image" from the Images API (or the model page), else OpenRouter's image token rate. */
    public static String imageGeneration(OrModel m, ImagePricing pricing) {
        String g = pricing != null ? pricing.generation() : null;
        if (g != null) return g;
        if (m.priceImageOutput != null && OrModel.value(m.priceImageOutput) != null) {
            return perMillion(m.priceImageOutput).replace("/ M tokens", "/ M image tokens");
        }
        return "price not listed by OpenRouter";
    }

    static boolean isPositive(String price) {
        BigDecimal v = OrModel.value(price);
        return v != null && v.signum() > 0;
    }

    /** Text input/output token prices apply to models that produce text, or that charge for them. */
    static boolean textPricesApply(OrModel m) {
        return !m.isImage() || m.outputsText() || isPositive(m.pricePrompt) || isPositive(m.priceCompletion);
    }

    /** Everything OpenRouter lists for a model, for its details sheet. */
    public static String modelDetails(OrModel m) {
        return modelDetails(m, null);
    }

    /** Everything OpenRouter lists for a model, each price in its own unit. */
    public static String modelDetails(OrModel m, ImagePricing pricing) {
        StringBuilder b = new StringBuilder();
        b.append("ID: ").append(m.id).append('\n');
        b.append("Provider: ").append(m.provider).append('\n');
        if (!m.isImage() || m.contextLength > 0) {
            b.append("Context window: ").append(m.contextLength > 0 ? count(m.contextLength) + " tokens" : "unknown").append('\n');
        }
        if (m.maxCompletion > 0 && m.outputsText()) b.append("Max output: ").append(count(m.maxCompletion)).append(" tokens\n");
        b.append("Input: ").append(m.input.isEmpty() ? "unknown" : join(m.input, ", ")).append('\n');
        b.append("Output: ").append(m.output.isEmpty() ? "unknown" : join(m.output, ", ")).append('\n');
        if (m.outputsText()) {
            b.append("Tool calling: ").append(m.parameters.isEmpty() ? "unknown" : m.supportsTools() ? "yes" : "no").append('\n');
        }
        b.append("\nPricing (US dollars, as listed by OpenRouter)\n");
        boolean imageLines = false;
        if (m.isImage()) {
            if (pricing != null && !pricing.isEmpty()) {
                for (String line : pricing.lines()) b.append(line).append('\n');
                imageLines = true;
                b.append(ImagePricing.MODEL_PAGE.equals(pricing.source)
                        ? "(From the model's openrouter.ai page: OpenRouter's API doesn't list this model's image prices.)\n"
                        : "(From OpenRouter's Images API.)\n");
            } else {
                b.append("Image generation: ").append(imageGeneration(m, null)).append('\n');
            }
        }
        if (textPricesApply(m)) {
            String in = m.isImage() ? "Text input" : "Input";
            String out = m.isImage() ? "Text output" : "Output";
            if (m.isImage() && !m.outputsText()) {
                if (isPositive(m.pricePrompt)) b.append("Prompt text: ").append(perMillion(m.pricePrompt)).append('\n');
            } else {
                b.append(in).append(": ").append(perMillion(m.pricePrompt)).append('\n');
                b.append(out).append(": ").append(perMillion(m.priceCompletion)).append('\n');
            }
        }
        if (m.priceReasoning != null) b.append("Internal reasoning: ").append(perMillion(m.priceReasoning)).append('\n');
        if (m.priceCacheRead != null) b.append("Cached input read: ").append(perMillion(m.priceCacheRead)).append('\n');
        if (m.priceCacheWrite != null) b.append("Cache write: ").append(perMillion(m.priceCacheWrite)).append('\n');
        if (!imageLines) {
            if (m.priceImage != null && isPositive(m.priceImage)) {
                // OpenRouter's pricing.image: the unit isn't stated, so it's shown as listed.
                b.append("Image input: ").append(perUnit(m.priceImage, "unit (pricing.image)")).append('\n');
            }
            if (m.priceImageOutput != null && !m.isImage()) {
                b.append("Image output: ").append(perMillion(m.priceImageOutput).replace("/ M tokens", "/ M image tokens")).append('\n');
            }
        }
        if (m.priceAudio != null) b.append("Audio input: ").append(perMillion(m.priceAudio)).append('\n');
        if (m.priceRequest != null && isPositive(m.priceRequest)) b.append("Per request (extra): ").append(perUnit(m.priceRequest, "request")).append('\n');
        if (m.priceWebSearch != null) b.append("Web search (extra): ").append(perUnit(m.priceWebSearch, "search")).append('\n');
        if (m.discount > 0) b.append("Discount: ").append(Math.round(m.discount * 100)).append("% off listed prices\n");
        if (m.hasPriceOverrides) b.append("Some prices change with prompt length or time of day (OpenRouter overrides).\n");
        if (m.isImage()) {
            b.append("The actual charge OpenRouter reports is shown after each generation.\n");
        }
        if (m.description.length() > 0) b.append('\n').append(m.description);
        return b.toString().trim();
    }
}
