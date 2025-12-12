WPP Gateway - Spring Boot (complete)
-----------------------------------

Instructions:
- Adjust src/main/resources/application.yml with your MySQL credentials and WPPConnect base URL/secret.
- Build: mvn -U clean package
- Run: java -jar target/wpp-gateway-0.0.1-SNAPSHOT.jar

Endpoints:
- POST /admin/create-client?name=ClienteA
- POST /api/create-session (header X-Api-Key) body { "sessionName":"..." }
- POST /api/start-session/{session} (header X-Api-Key)
- POST /api/messages/send (header X-Api-Key) body { session, to, message }
