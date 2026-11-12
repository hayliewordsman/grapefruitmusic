package io.github.hayliewordsman.grapefruitai.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.math.BigDecimal;

public class FormatTest {
    @Test
    public void usd() {
        assertEquals("Free", Format.usd(BigDecimal.ZERO));
        assertEquals("variable", Format.usd(new BigDecimal("-1")));
        assertEquals("not listed", Format.usd(null));
        assertEquals("$3.00", Format.usd(new BigDecimal("3")));
        assertEquals("$0.15", Format.usd(new BigDecimal("0.15")));
        assertEquals("$0.007", Format.usd(new BigDecimal("0.007")));
        assertEquals("$0.0004", Format.usd(new BigDecimal("0.0004")));
        assertEquals("$150", Format.usd(new BigDecimal("150")));
    }

    @Test
    public void perMillion() {
        assertEquals("$3.00 / M tokens", Format.perMillion("0.000003"));
        assertEquals("$0.075 / M tokens", Format.perMillion("0.000000075"));
        assertEquals("Free", Format.perMillion("0"));
        assertEquals("not listed", Format.perMillion(null));
        assertEquals("not listed", Format.perMillion("abc"));
    }

    @Test
    public void tokens() {
        assertEquals("128K", Format.tokens(128000));
        assertEquals("1M", Format.tokens(1000000));
        assertEquals("1M", Format.tokens(1048576));
        assertEquals("2.5M", Format.tokens(2500000));
        assertEquals("unknown", Format.tokens(0));
    }
}
