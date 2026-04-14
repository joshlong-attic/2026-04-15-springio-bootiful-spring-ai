package com.example.agent;

import org.springaicommunity.agent.advisors.AutoMemoryToolsAdvisor;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.sql.DataSource;
import java.io.File;
import java.nio.charset.Charset;
import java.util.List;

@SpringBootApplication
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }

    @Bean
    QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        return QuestionAnswerAdvisor
                .builder(vectorStore)
                .build();
    }

    @Bean
    PromptChatMemoryAdvisor promptChatMemoryAdvisor(DataSource dataSource) {
        var jdbc = JdbcChatMemoryRepository.builder().dataSource(dataSource).build();
        var mwa = MessageWindowChatMemory
                .builder()
                .chatMemoryRepository(jdbc)
                .build();
        return PromptChatMemoryAdvisor
                .builder(mwa)
                .build();
    }

    @Bean
    AutoMemoryToolsAdvisor autoMemoryToolsAdvisor(@Value("file://${user.home}/Desktop/memory") File memoryDirectory) {
        return AutoMemoryToolsAdvisor.builder()
//                .memoryConsolidationTrigger((chatClientRequest, _) -> true)
                .memoriesRootDirectory(memoryDirectory.getAbsolutePath())
                .build();
    }

    @Bean
    LoggingAdvisor loggingAdvisor() {
        // Custom logging advisor
        return LoggingAdvisor.builder()
                .showAvailableTools(false)
                .showSystemMessage(false)
                .build();

    }
}


record Dog(@Id int id, String name, String description) {
}

interface DogRepository extends ListCrudRepository<Dog, Integer> {
}

@Controller
@ResponseBody
class AgentController {

    private final ChatClient ai;

    AgentController(
            LoggingAdvisor loggingAdvisor,
            PromptChatMemoryAdvisor promptChatMemoryAdvisor,
            AutoMemoryToolsAdvisor autoMemoryToolsAdvisor,
            QuestionAnswerAdvisor questionAnswerAdvisor,
            VectorStore vectorStore,
            DogRepository repository,
            ChatClient.Builder ai,
            @Value("classpath:/prompt/MAIN_AGENT_SYSTEM_PROMPT_V2.md") Resource mainAgentSystemPromptV2) throws Exception {
        Assert.state(mainAgentSystemPromptV2.exists(), "the prompt should exist");

        IO.println("prompt: " + mainAgentSystemPromptV2.getContentAsString(Charset.defaultCharset()));

        if ( false ) {
            repository.findAll().forEach(dog -> {
                var dogument = new Document("id: %s, name: %s, description: %s".
                        formatted(dog.id(), dog.name(), dog.description()));
                vectorStore.add(List.of(dogument));
            });
        }

        var tool = ToolCallAdvisor
                .builder()
                .disableInternalConversationHistory()
                .build();

        // question: should i adopt a dog or a cat?
        var skill = SkillsTool
                .builder()
                .addSkillsResource(new ClassPathResource("/META-INF/skills"))
                .build();

        this.ai = ai
                .defaultAdvisors(loggingAdvisor, autoMemoryToolsAdvisor, tool,promptChatMemoryAdvisor ,
                        questionAnswerAdvisor )
                .defaultToolCallbacks(skill)
                .defaultSystem(p -> p
                        .text("""
                                    You are an AI powered assistant to help people adopt a dog from the adoptions agency named Pooch Palace\s
                                    with locations in Antwerp, Seoul, Tokyo, Singapore, Paris, Mumbai, New Delhi, Barcelona, San Francisco,\s
                                    and London. Information about the dogs availables will be presented below. If there is no information,\s
                                    then return a polite response suggesting wes don't have any dogs available.
                                
                                    If somebody asks you about prospect animals (dogs, cats) in the adoption shelter, and there's no 
                                    information in the context, then feel free to source the answer from other places.
                                
                                    Help people schedule appointments to pick up dogs from the Pooch Palace adoption shelter. If someone 
                                    asks for a date, use the tools to get a valid appointment date/time and return the response without further questioning.
                                """)
                        .text(mainAgentSystemPromptV2)
                )
                .build();
    }

    @GetMapping("/{userId}/ask")
    String ask(
            @PathVariable String userId,
            @RequestParam String question) {
        return this.ai
                .prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userId))
                .call()
                .content();
    }
}

// record DogAdoptionSuggestion (int id,String name) {}

class LoggingAdvisor implements BaseAdvisor {

    private static final String ORANGE = "\033[38;5;208m";

    private static final String RESET = "\033[0m";

    private final int order;

    public final boolean showSystemMessage;

    public final boolean showAvailableTools;

    public final boolean showConversationHistory;

    public final String labelPrefix;

    private LoggingAdvisor(int order, boolean showSystemMessage, boolean showAvailableTools,
                           boolean showConversationHistory, String labelPrefix) {
        this.order = order;
        this.showSystemMessage = showSystemMessage;
        this.showAvailableTools = showAvailableTools;
        this.showConversationHistory = showConversationHistory;
        this.labelPrefix = labelPrefix;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override

    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {

        StringBuilder sb = new StringBuilder("\n" + this.labelPrefix + "USER: ");

        if (this.showSystemMessage && chatClientRequest.prompt().getSystemMessage() != null
                && StringUtils.hasText(chatClientRequest.prompt().getSystemMessage().getText())) {
            sb.append("\n - SYSTEM: " + first(chatClientRequest.prompt().getSystemMessage().getText(), 50));
        }

        if (this.showAvailableTools) {
            Object tools = "No Tools";

            if (chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions toolOptions) {
                tools = toolOptions.getToolCallbacks().stream().map(tc -> tc.getToolDefinition().name()).toList();
            }

            sb.append("\n - TOOLS: " + ModelOptionsUtils.toJsonString(tools));
        }

        Message lastMessage = chatClientRequest.prompt().getLastUserOrToolResponseMessage();

        if (lastMessage.getMessageType() == MessageType.TOOL) {
            ToolResponseMessage toolResponseMessage = (ToolResponseMessage) lastMessage;
            for (var toolResponse : toolResponseMessage.getResponses()) {
                var tr = toolResponse.name() + ": " + first(toolResponse.responseData(), 300);
                sb.append("\n - TOOL-RESPONSE: " + tr);
            }
        } else if (lastMessage.getMessageType() == MessageType.USER) {
            if (StringUtils.hasText(lastMessage.getText())) {
                sb.append("\n - TEXT: " + first(lastMessage.getText(), 300));
            }
        }

        if (this.showConversationHistory) {
            sb.append("\n - [MEMORY]: " + chatClientRequest.prompt()
                    .getInstructions()
                    .stream()
                    .map(m -> m.getMessageType() + ": " + messageContent(m))
                    .toList());
        }

        System.out.println(ORANGE + sb.toString() + RESET);

        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        StringBuilder sb = new StringBuilder("\n" + this.labelPrefix + "ASSISTANT: ");

        if (chatClientResponse.chatResponse() == null || chatClientResponse.chatResponse().getResults() == null) {
            sb.append(" No chat response ");
            System.out.println(sb.toString());
            return chatClientResponse;
        }

        for (var generation : chatClientResponse.chatResponse().getResults()) {
            var message = generation.getOutput();
            if (message.getToolCalls() != null) {
                for (var toolCall : message.getToolCalls()) {
                    sb.append("\n - TOOL-CALL: ")
                            .append(toolCall.name())
                            .append(" (")
                            .append(toolCall.arguments())
                            .append(")");
                }
            }

            if (message.getText() != null) {
                if (StringUtils.hasText(message.getText())) {
                    sb.append("\n - TEXT: " + first(message.getText(), 200));
                }
            }
        }

        System.out.println(ORANGE + sb.toString() + RESET);

        return chatClientResponse;
    }

    private String first(String text, int n) {
        if (text.length() <= n) {
            return text;
        }
        return text.substring(0, n) + "...";
    }

    private String messageContent(Message message) {
        if (message instanceof ToolResponseMessage toolResponseMessage) {
            return toolResponseMessage.getResponses()
                    .stream()
                    .map(r -> first(r.name() + ": " + r.responseData(), 30))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
        } else if (message instanceof AssistantMessage assistantMessage) {
            if (StringUtils.hasText(assistantMessage.getText())) {
                return first(assistantMessage.getText(), 20);
            }
            return assistantMessage.getToolCalls() != null ?
                    assistantMessage
                    .getToolCalls()
                    .stream()
                    .map(tc -> first(tc.name() + ": " + tc.arguments(), 30))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("") : "";
        } else {
            if (message.getText() != null) {
                return first(message.getText(), 30);
            }
            return "";
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private int order = 0;

        private boolean showSystemMessage = true;

        private boolean showAvailableTools = false;

        private boolean showConversationHistory = false;

        private String labelPrefix = "";

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder showSystemMessage(boolean showSystemMessage) {
            this.showSystemMessage = showSystemMessage;
            return this;
        }

        public Builder showAvailableTools(boolean showAvailableTools) {
            this.showAvailableTools = showAvailableTools;
            return this;
        }

        public Builder showConversationHistory(boolean showConversationHistory) {
            this.showConversationHistory = showConversationHistory;
            return this;
        }

        public Builder labelPrefix(String labelPrefix) {
            this.labelPrefix = labelPrefix;
            return this;
        }

        public LoggingAdvisor build() {
            LoggingAdvisor advisor = new LoggingAdvisor(this.order, this.showSystemMessage, this.showAvailableTools,
                    this.showConversationHistory, this.labelPrefix);
            return advisor;
        }

    }

}