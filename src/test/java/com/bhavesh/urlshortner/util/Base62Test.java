package com.bhavesh.urlshortner.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Base62Test {

    @Test
    void encodesZero() {
        assertEquals("0", Base62.encode(0));
    }

    @Test
    void encodesBoundaryValues() {
        assertEquals("9", Base62.encode(9));
        assertEquals("a", Base62.encode(10));
        assertEquals("z", Base62.encode(35));
        assertEquals("A", Base62.encode(36));
        assertEquals("Z", Base62.encode(61));
        assertEquals("10", Base62.encode(62));
        assertEquals("100", Base62.encode(3_844));
    }

    @Test
    void sevenCharactersAtSixtyTwoToTheSixth() {
        assertEquals("1000000", Base62.encode(56_800_235_584L));
    }

    @Test
    void rejectsNegativeInput() {
        assertThrows(IllegalArgumentException.class, () -> Base62.encode(-1));
    }
}