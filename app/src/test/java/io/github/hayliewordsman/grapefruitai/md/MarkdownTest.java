package io.github.hayliewordsman.grapefruitai.md;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

public class MarkdownTest {
    @Test
    public void blocks() {
        List<Markdown.Block> b = Markdown.parse("# Title\n\nSome *text*\nmore\n\n- one\n- two\n  continued\n1. first\n"
                + "> quote\n\n---\n```java\nint x = 1;\n```\n| a | b |\n|---|---|\n| 1 | 2 |\n");
        assertEquals(Markdown.HEADING, b.get(0).type);
        assertEquals("Title", b.get(0).text);
        assertEquals(Markdown.PARAGRAPH, b.get(1).type);
        assertEquals("Some *text*\nmore", b.get(1).text);
        assertEquals(Markdown.BULLET, b.get(2).type);
        assertEquals("two continued", b.get(3).text);
        assertEquals(Markdown.ORDERED, b.get(4).type);
        assertEquals(Markdown.QUOTE, b.get(5).type);
        assertEquals(Markdown.RULE, b.get(6).type);
        assertEquals(Markdown.CODE, b.get(7).type);
        assertEquals("java", b.get(7).info);
        assertEquals("int x = 1;", b.get(7).text);
        assertEquals(Markdown.TABLE, b.get(8).type);
        assertEquals(9, b.size());
    }

    @Test
    public void unclosedFenceWhileStreaming() {
        List<Markdown.Block> b = Markdown.parse("Here:\n```kotlin\nfun main() {\n    println(\"hi\")");
        assertEquals(2, b.size());
        assertEquals(Markdown.CODE, b.get(1).type);
        assertEquals("fun main() {\n    println(\"hi\")", b.get(1).text);
    }

    @Test
    public void fenceKeepsMarkdownInside() {
        List<Markdown.Block> b = Markdown.parse("```\n# not a heading\n- not a list\n```");
        assertEquals(1, b.size());
        assertEquals("# not a heading\n- not a list", b.get(0).text);
    }

    @Test
    public void longerFencesNeedLongerClose() {
        List<Markdown.Block> b = Markdown.parse("````md\n```\ninner\n```\n````\nafter");
        assertEquals(Markdown.CODE, b.get(0).type);
        assertEquals("```\ninner\n```", b.get(0).text);
        assertEquals("after", b.get(1).text);
    }

    @Test
    public void tableCells() {
        assertEquals(3, Markdown.cells("| a | b | c |").size());
        assertEquals("b", Markdown.cells("a | b").get(1));
    }
}
