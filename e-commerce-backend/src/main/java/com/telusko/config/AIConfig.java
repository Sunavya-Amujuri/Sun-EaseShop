package com.telusko.config;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AIConfig

{

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder().build();
    }

    /** The conversational client, used where chat history is wanted. */
    @Bean
    @Primary
    public ChatClient chatClient(ChatModel chatModel,ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * A client with no chat memory attached, for one-shot calls: classification, summarising,
     * writing a suggestion blurb.
     * <p>
     * These must not use the conversational client. Its memory advisor would record every
     * background call into a shared conversation, growing the window with text no user ever wrote
     * and leaking it into the next reply.
     */
    @Bean("oneShotChatClient")
    public ChatClient oneShotChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    /**
     * Turns a conversational follow-up into a standalone question before it is used for retrieval.
     * <p>
     * Embeddings have no memory of the conversation. "Is it available in blue?" on its own is
     * close to nothing in the vector store, so the assistant would retrieve noise for exactly the
     * kind of short follow-up people actually type. Rewriting it to something like "Is the
     * Nike Air Max running shoe available in blue?" is what makes multi-turn RAG work.
     */
    @Bean
    public QueryTransformer queryRewriter(ChatModel chatModel) {
        // Deliberately built on a bare ChatClient: this rewrite must not be recorded in, or
        // influenced by, the user's own chat memory.
        return RewriteQueryTransformer.builder()
                .chatClientBuilder(ChatClient.builder(chatModel))
                .build();
    }
}
