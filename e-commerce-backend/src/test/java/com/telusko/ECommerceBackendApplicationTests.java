package com.telusko;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Uses the same datasource the application does, so the local Postgres has to be running for this
// test to load the context.
@SpringBootTest
class ECommerceBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
