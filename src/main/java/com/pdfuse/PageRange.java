package com.pdfuse;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Plage de pages 1-based, inclusive, ex. {@code 1-3,7,10-12}. */
public final class PageRange {

    private record Range(int start, int end) {
    }

    private final List<Range> ranges;

    private PageRange(List<Range> ranges) {
        this.ranges = ranges;
    }

    public static PageRange parse(String text) throws MergeException {
        if (text == null || text.isBlank()) {
            throw new MergeException("empty page range");
        }
        List<Range> parsed = new ArrayList<>();
        for (String token : text.split(",", -1)) {
            String value = token.trim();
            if (value.isEmpty()) {
                throw new MergeException("invalid page range: " + text);
            }
            int dash = value.indexOf('-');
            if (dash < 0) {
                int page = positive(value, text);
                parsed.add(new Range(page, page));
            } else {
                if (dash != value.lastIndexOf('-')) {
                    throw new MergeException("invalid page range: " + text);
                }
                int start = positive(value.substring(0, dash), text);
                int end = positive(value.substring(dash + 1), text);
                if (end < start) {
                    throw new MergeException("invalid page range " + value
                            + ": end is before start");
                }
                parsed.add(new Range(start, end));
            }
        }
        return new PageRange(parsed);
    }

    private static int positive(String value, String context) throws MergeException {
        try {
            int page = Integer.parseInt(value.trim());
            if (page < 1) {
                throw new NumberFormatException();
            }
            return page;
        } catch (NumberFormatException e) {
            throw new MergeException("invalid page range: " + context);
        }
    }

    /** Retourne les index 0-based, triés et sans doublon. */
    public List<Integer> resolve(int pageCount) throws MergeException {
        TreeSet<Integer> pages = new TreeSet<>();
        for (Range range : ranges) {
            if (range.end() > pageCount) {
                throw new MergeException("page range " + range.start() + "-" + range.end()
                        + " exceeds document page count (" + pageCount + ")");
            }
            for (int page = range.start(); page <= range.end(); page++) {
                pages.add(page - 1);
            }
        }
        return List.copyOf(pages);
    }
}
