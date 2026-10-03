package com.xnvalabs.smarteyex.data.xnai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EndpointRulesTest {
    @Test fun acceptsHttpsAndTrimsTrailingSlash() {
        assertEquals("https://xnai.example.com", EndpointRules.normalize(" https://xnai.example.com/ "))
        assertEquals("https://xnai.example.com/api", EndpointRules.normalize("https://xnai.example.com/api//"))
    }

    @Test fun rejectsEmptyHttpAndHostless() {
        assertNull(EndpointRules.normalize(null))
        assertNull(EndpointRules.normalize(""))
        assertNull(EndpointRules.normalize("   "))
        assertNull(EndpointRules.normalize("http://xnai.example.com"))
        assertNull(EndpointRules.normalize("https://"))
        assertNull(EndpointRules.normalize("ftp://xnai.example.com"))
    }
}
