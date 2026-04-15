package com.example.doghouse;

import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.sql.DataSource;
import java.util.List;

import static org.springaicommunity.mcp.security.client.sync.config.McpClientOAuth2Configurer.mcpClientOAuth2;

@SpringBootApplication
public class DoghouseApplication {

    public static void main(String[] args) {
        SpringApplication.run(DoghouseApplication.class, args);
    }

    @Bean
    PromptChatMemoryAdvisor promptChatMemoryAdvisor(DataSource dataSource) {
        var jdbc = JdbcChatMemoryRepository
                .builder()
                .dataSource(dataSource)
                .build();

        var mwa = MessageWindowChatMemory
                .builder()
                .chatMemoryRepository(jdbc)
                .build();
        return PromptChatMemoryAdvisor
                .builder(mwa).build();
    }

    @Bean
    ToolCallAdvisor toolCallAdvisor() {
        return ToolCallAdvisor
                .builder()
                .conversationHistoryEnabled(true)
                .build();
    }

    @Bean
    QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        return QuestionAnswerAdvisor.builder(vectorStore).build();
    }

    @Bean
    Customizer<HttpSecurity> httpSecurityCustomizer() {
        return http -> http.with(mcpClientOAuth2());
    }
}

// look mom, no Lombok!
record Dog(@Id int id, String name, String description) {
}

interface DogRepository extends ListCrudRepository<Dog, Integer> {
}

@Controller
@ResponseBody
class AssistantController {


    private final ChatClient ai;

    AssistantController(
            ToolCallbackProvider dogAdoptionScheduler,
            QuestionAnswerAdvisor questionAnswerAdvisor,
            DogRepository repository, VectorStore vectorStore,
            PromptChatMemoryAdvisor promptChatMemoryAdvisor,
            ToolCallAdvisor toolCallAdvisor,
            ChatClient.Builder ai) {

        if (false) {
            repository.findAll().forEach(dog -> {
                var dogument = new Document("id: %s, name: %s, description: %s".formatted(
                        dog.id(), dog.name(), dog.description()
                ));
                vectorStore.add(List.of(dogument));
            });
        }
        var system = """
                You are an AI powered assistant to help people adopt a dog from the adoptions agency named Pooch Palace\s
                with locations in Antwerp, Seoul, Tokyo, Singapore, Paris, Mumbai, New Delhi, Barcelona, San Francisco,\s
                and London. Information about the dogs availables will be presented below. If there is no information,\s
                then return a polite response suggesting we don't have any dogs available.
                
                If somebody asks you about animals, and there's no information in the context, then feel free to source the answer from other places.
                
                If somebody asks for a time to pick up the dog, don't ask other questions: simply provide a time by consulting the tools you have available.
                
                """;
        var skill = SkillsTool
                .builder()
                .addSkillsResource(new ClassPathResource("/META-INF/skills"))
                .build();
        this.ai = ai
                .defaultSystem(system)
                .defaultToolCallbacks(dogAdoptionScheduler)
                .defaultAdvisors(promptChatMemoryAdvisor,
                        questionAnswerAdvisor,
                        toolCallAdvisor)
                .defaultToolCallbacks(skill)
                .build();
    }

    @GetMapping("/ask")
    String ask () {
        return this.ai
                .prompt()
                .user("fantastic. when can i pick up Prancer from the Barcelona Pooch Palace location?")
//                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, user))
                .call()
                .content();
    }
}


record DogAdoptionSuggestion(int id, String name) {
}