package com.autoerd;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class AutoErdGeneratorApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void rootPageShouldDisableCaching() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(view().name("index"))
				.andExpect(header().string("Cache-Control", containsString("no-store")))
				.andExpect(header().string("Pragma", "no-cache"));
	}

	@Test
	void indexHtmlAliasShouldRenderSameTemplate() throws Exception {
		mockMvc.perform(get("/index.html"))
				.andExpect(status().isOk())
				.andExpect(view().name("index"))
				.andExpect(header().string("Cache-Control", containsString("no-store")));
	}

	@Test
	void privateNetworkPreflightShouldReturnExpectedHeaders() throws Exception {
		mockMvc.perform(options("/api/v1/erd/generate")
						.header("Origin", "http://example.com")
						.header("Access-Control-Request-Method", "POST")
						.header("Access-Control-Request-Headers", "content-type")
						.header("Access-Control-Request-Private-Network", "true"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://example.com"))
				.andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
				.andExpect(header().string("Access-Control-Allow-Headers", "content-type"))
				.andExpect(header().string("Access-Control-Allow-Private-Network", "true"));
	}
}
