# Dataset Creating AI Assistant

A Spring Boot application that serves as an AI-powered assistant to generate training datasets using MCP (Model Context Protocol) for web content extraction and email notifications.

## Overview

This application provides:
- Content extraction from target websites
- Generation of training datasets in JSON format
- Optional email notifications upon dataset completion
- Conversation history with memory management
- MCP tools integration for web scraping and mailing

## Features

- **AI Chat Interface**: RESTful API for conversational AI interactions
- **Dataset Generation**: Automated creation of training datasets from any website
- **Web Content Extraction**: MCP fetch tools for different content types (markdown, HTML, raw text)
- **Memory Management**: Conversation history with configurable message window
- **French Language Support**: All interactions and outputs in French
- **Email Notifications**: Automatic emails when dataset generation is completed (via custom MCP SMTP server)
- **Transport Support**: STDIO for fetch server and SSE for email server

## Technology Stack

- **Java 21**
- **Spring Boot 3.5.3**
- **Spring AI 1.0.0**
- **Groq AI API** (Llama models)
- **MCP SDK 0.11.0**
- **MCP Client for Spring AI**

## Prerequisites

- Java 21 or higher
- Maven 3.6+
- Node.js (for MCP servers)
- Access to Groq API

## MCP Servers Setup

### Web Fetch Server (STDIO Transport)
Use the [TypeScript-based MCP fetch server](https://github.com/tatn/mcp-server-fetch-typescript.git):
```bash
git clone https://github.com/tatn/mcp-server-fetch-typescript.git
# Build and configure path in mcp-servers.json
```
Custom MCP Email Server (SSE Transport)
Use the Java MCP SMTP server:
```bash
git clone https://github.com/RayenMalouche/Java-MCP-Server-For-SMTP-Mailing.git
# Follow setup instructions in the repository and configure SMTP settings
# Start server (typically runs on localhost:45450)*
```
## Configuration

### Application Properties Example
```properties
spring.application.name=Dataset-MCP-Assistant
server.port=8072

# Groq AI Configuration
spring.ai.openai.api-key=your_groq_api_key
spring.ai.openai.base-url=https://api.groq.com/openai
spring.ai.openai.chat.options.model=llama-3.1-8b-instant

# MCP STDIO and SSE Configuration
spring.ai.mcp.client.stdio.servers-configuration=classpath:mcp-servers.json
spring.ai.mcp.client.sse.connections.smtp-email-server.url=http://localhost:45450/sse
#keep one of these two lines
spring.ai.mcp.client.sse.connections.performance-test-server.url=http://localhost:45451/sse
spring.ai.mcp.client.enabled=true
#keep one of the two lines above: 1st for sse connection and 2nd for stdio transport
spring.ai.mcp.client.toolcallback.enabled=true
spring.ai.mcp.client.tool.name-prefix-enabled=false

# Logging
logging.level.org.springframework.ai=DEBUG
logging.level.io.modelcontextprotocol=TRACE
```

### MCP Servers Configuration (`mcp-servers.json`)
```json
{
  "mcpServers": {
    "mcp-server-fetch-typescript": {
      "command": "node",
      "args": ["path/to/mcp-server-fetch-typescript/build/index.js"]
    }
  }
}
```

## Installation & Setup

### Clone the repository:
```bash
git clone https://github.com/RayenMalouche/AI-Agents-Performance-Testing-MCP-Client.git
cd AI-Agents-Performance-Testing-MCP-Client
```

### Configure API keys:
- Update `application.properties` with your Groq API key
- Configure MCP server paths as shown above

### Setup MCP Servers:
- Build and configure the fetch server
- Optionally set up the email server for notifications

### Build and run the Spring Boot application:
```bash
mvn clean install
mvn spring-boot:run
```

## API Endpoints

### Chat Interface
```bash
GET /assistant/prompt?userId={userId}&message={message}
```
- **userId**: Unique identifier for conversation context
- **message**: User query in French

### Conversation Management
```bash
GET /assistant/{userId}/history
DELETE /assistant/{userId}/history
```

## MCP Tools Available

### Web Content Extraction (STDIO)
- `get_raw_text`: Extract raw text from URLs
- `get_rendered_html`: Get complete HTML after JavaScript execution
- `get_markdown`: Convert content to structured Markdown
- `get_markdown_summary`: Extract main content as clean Markdown

### Email Notifications (SSE)
- `send-email`: Send SMTP email notifications via custom MCP server  
  Parameters: `to`, `subject`, `body`, `cc` (optional), `bcc` (optional)

## Dataset Generation Format

Generated datasets follow the format:
```json
{
  "Input": "- Contexte : [extracted context]. - Question : [specific question].",
  "Output": "[Response based on extracted content]"
}
```

## Monitoring & Logging
- Logs: `./ai-agent-visitor.log`
- Debug level logging for Spring AI and MCP components
- Actuator endpoints available for health monitoring

## Error Handling
- Graceful error messages for invalid URLs or missing data
- Email sending failures are logged but do not stop dataset generation

## Contributing
1. Fork the repository
2. Create a feature branch
3. Make changes and test with both MCP servers (if using email features)
4. Submit a pull request

## Support
For issues related to:
- **Application functionality**: Create an issue in this repository
- **MCP Email Server**: See [Java-MCP-Server-For-SMTP-Mailing](https://github.com/RayenMalouche/Java-MCP-Server-For-SMTP-Mailing)
- **MCP Performance Testing Server (java)**: See [AI-Agents-Performance-Testing-MCP-Server
  ](https://github.com/RayenMalouche/AI-Agents-Performance-Testing-MCP-Server.git)
- **MCP Performance Testing Server (TypeScript)**: See [AI-Agents-Performance-Testing-MCP-TypeScript-Server](https://github.com/RayenMalouche/AI-Agents-Performance-Testing-MCP-TypeScript-Server.git)