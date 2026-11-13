package io.github.hayliewordsman.grapefruitai.or;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Searching, filtering and sorting the model list. */
public final class Catalog {
    public static final int ALL = 0, CHAT = 1, CODING = 2, IMAGE = 3;
    public static final String[] KIND_NAMES = {"All", "Chat", "Coding", "Image"};

    public static final int SORT_NAME = 0, SORT_PROVIDER = 1, SORT_INPUT_PRICE = 2, SORT_OUTPUT_PRICE = 3,
            SORT_CONTEXT = 4, SORT_NEWEST = 5;
    public static final String[] SORT_NAMES = {"Name", "Provider", "Input price", "Output price", "Context", "Newest"};

    /** Capability filters (AND). */
    public boolean needTools, needVision, needFiles, needReasoning, freeOnly;
    public int kind = ALL;
    /** Hard limit regardless of the chips: CHAT (chat-capable only), IMAGE (image models only) or ALL. */
    public int restrict = ALL;
    public int sort = SORT_NAME;
    public String query = "";

    public List<OrModel> apply(List<OrModel> models) {
        List<OrModel> out = new ArrayList<OrModel>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.US);
        String[] words = q.length() == 0 ? new String[0] : q.split("\\s+");
        for (OrModel m : models) {
            if (restrict == CHAT && Routing.chatBlock(m) != null) continue;
            if (restrict == IMAGE && !m.isImage()) continue;
            if (!matchesKind(m, kind)) continue;
            if (needTools && !m.supportsTools()) continue;
            if (needVision && !m.acceptsImages()) continue;
            if (needFiles && !m.acceptsFiles()) continue;
            if (needReasoning && !m.supportsReasoning()) continue;
            if (freeOnly && !m.isFree()) continue;
            if (!matchesWords(m, words)) continue;
            out.add(m);
        }
        Collections.sort(out, comparator(sort));
        return out;
    }

    public static boolean matchesKind(OrModel m, int kind) {
        switch (kind) {
            case CHAT: return m.isChat();
            case CODING: return m.isCoding();
            case IMAGE: return m.isImage();
            default: return true;
        }
    }

    private static boolean matchesWords(OrModel m, String[] words) {
        if (words.length == 0) return true;
        String hay = (m.name + " " + m.id + " " + m.provider + " " + join(m.input) + " " + join(m.output)
                + (m.supportsTools() ? " tools" : "")).toLowerCase(Locale.US);
        for (String w : words) if (!hay.contains(w)) return false;
        return true;
    }

    private static String join(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String s : l) b.append(s).append(' ');
        return b.toString();
    }

    public static Comparator<OrModel> comparator(int sort) {
        final Comparator<OrModel> byName = new Comparator<OrModel>() {
            @Override
            public int compare(OrModel a, OrModel b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        };
        switch (sort) {
            case SORT_PROVIDER:
                return new Comparator<OrModel>() {
                    @Override
                    public int compare(OrModel a, OrModel b) {
                        int c = a.provider.compareToIgnoreCase(b.provider);
                        return c != 0 ? c : byName.compare(a, b);
                    }
                };
            case SORT_INPUT_PRICE:
                return new Comparator<OrModel>() {
                    @Override
                    public int compare(OrModel a, OrModel b) {
                        int c = Double.compare(a.sortInputPrice(), b.sortInputPrice());
                        return c != 0 ? c : byName.compare(a, b);
                    }
                };
            case SORT_OUTPUT_PRICE:
                return new Comparator<OrModel>() {
                    @Override
                    public int compare(OrModel a, OrModel b) {
                        int c = Double.compare(a.sortOutputPrice(), b.sortOutputPrice());
                        return c != 0 ? c : byName.compare(a, b);
                    }
                };
            case SORT_CONTEXT:
                return new Comparator<OrModel>() {
                    @Override
                    public int compare(OrModel a, OrModel b) {
                        int c = a.contextLength < b.contextLength ? 1 : a.contextLength > b.contextLength ? -1 : 0;
                        return c != 0 ? c : byName.compare(a, b);
                    }
                };
            case SORT_NEWEST:
                return new Comparator<OrModel>() {
                    @Override
                    public int compare(OrModel a, OrModel b) {
                        int c = a.created < b.created ? 1 : a.created > b.created ? -1 : 0;
                        return c != 0 ? c : byName.compare(a, b);
                    }
                };
            default:
                return byName;
        }
    }

    public static OrModel find(List<OrModel> models, String id) {
        if (models == null || id == null) return null;
        for (OrModel m : models) if (m.id.equals(id)) return m;
        return null;
    }
}
