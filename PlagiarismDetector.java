import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

// Main class for our plagiarism detection system
public class PlagiarismDetector {
    
    // Sample reference texts for comparison. In a real application, this would be a real database.
    private static final String[] REFERENCE_TEXTS = {
        "The quick brown fox jumps over the lazy dog. This is a classic sentence used for typography samples.",
        "Java is a high-level, class-based, object-oriented programming language that is designed to have as few implementation dependencies as possible.",
        "Machine learning is a field of inquiry devoted to understanding and building methods that 'learn', that is, methods that leverage data to improve performance on some set of tasks.",
        "The World Wide Web, commonly known as the Web, is an information system where documents and other web resources are identified by Uniform Resource Locators.",
        "Data structures are a way of organizing and storing data in a computer so that it can be accessed and modified efficiently."
    };

    // Main method: This is where our program starts
    public static void main(String[] args) {
        try {
            // Create a web server that listens on port 8080
            System.out.println("🚀 Starting Plagiarism Detection Server...");
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
            
            // The "/check" endpoint will handle plagiarism detection requests from the frontend
            server.createContext("/check", new PlagiarismHandler());
            
            // Set up a thread pool to handle multiple requests at once
            server.setExecutor(Executors.newFixedThreadPool(10));
            
            // Start the server
            server.start();
            System.out.println("✅ Server is running on http://localhost:8080");
            System.out.println("   (Frontend should send requests to http://localhost:8080/check)");
            
        } catch (IOException e) {
            System.out.println("❌ Error starting server: " + e.getMessage());
        }
    }

    // Handler for plagiarism detection requests
    static class PlagiarismHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Enable CORS (Cross-Origin Resource Sharing) to allow requests from the browser
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            // Browsers often send a pre-flight "OPTIONS" request first for CORS
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); // No Content
                return;
            }

            // We only want to handle POST requests for plagiarism checking
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "{\"error\":\"Only POST method is allowed\"}");
                return;
            }

            try {
                // Read the request body (the data sent from the frontend)
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                
                // Parse the request to get the text content
                String inputText = parseRequest(requestBody);
                
                if (inputText == null || inputText.trim().isEmpty()) {
                    sendErrorResponse(exchange, 400, "{\"error\":\"No text content provided\"}");
                    return;
                }

                // Check for plagiarism by comparing with our reference texts
                double plagiarismPercent = checkPlagiarism(inputText);

                // Create a response message based on the plagiarism percentage
                String message = getResultMessage(plagiarismPercent);

                // Create JSON response
                String jsonResponse = String.format(
                    "{\"percentage\":%.1f,\"message\":\"%s\"}", 
                    plagiarismPercent, message
                );

                // Send the JSON response back to the frontend
                sendSuccessResponse(exchange, jsonResponse);

            } catch (Exception e) {
                // If there's any server-side error, send an error response
                sendErrorResponse(exchange, 500, "{\"error\":\"Server error: " + e.getMessage() + "\"}");
            }
        }
    }

    // --- Helper Methods ---

    // Method to parse the request body and extract the text content
    private static String parseRequest(String body) {
        if (body.startsWith("text=")) {
            String encodedText = body.substring(5);
            return URLDecoder.decode(encodedText, StandardCharsets.UTF_8);
        }
        return body;
    }

    // Method to check for plagiarism by comparing input text with reference texts
    private static double checkPlagiarism(String inputText) {
        // 1. Pre-process the input text: convert to lowercase, remove punctuation, and split into words
        Set<String> inputWords = preprocessText(inputText);
        
        if (inputWords.isEmpty()) {
            return 0.0;
        }

        double maxSimilarity = 0.0;

        // 2. Compare the input text with each reference text
        for (String referenceText : REFERENCE_TEXTS) {
            Set<String> referenceWords = preprocessText(referenceText);
            if (referenceWords.isEmpty()) continue;

            // 3. Calculate the similarity score
            double similarity = calculateJaccardSimilarity(inputWords, referenceWords);
            
            // 4. Keep track of the highest similarity score found
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
            }
        }

        // Return the highest similarity percentage found
        return maxSimilarity * 100.0;
    }

    // Method to clean and tokenize text into a set of unique words
    private static Set<String> preprocessText(String text) {
        // Convert to lowercase and remove all non-alphanumeric characters
        String cleanText = text.toLowerCase().replaceAll("[^a-z0-9\\s]", "");
        // Split by whitespace and filter out any empty strings
        return Arrays.stream(cleanText.split("\\s+"))
                     .filter(word -> !word.isEmpty())
                     .collect(Collectors.toSet());
    }

    // Method to calculate Jaccard Similarity between two sets of words
    private static double calculateJaccardSimilarity(Set<String> set1, Set<String> set2) {
        if (set1.isEmpty() || set2.isEmpty()) return 0.0;

        // Find the intersection (common words)
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        // Find the union (all unique words from both sets)
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        // Jaccard Similarity = |Intersection| / |Union|
        return (double) intersection.size() / union.size();
    }
    
    // Generates a user-friendly message based on the score
    private static String getResultMessage(double percentage) {
        if (percentage >= 70) {
            return "A high degree of similarity was found. This text requires immediate and thorough review for plagiarism.";
        } else if (percentage >= 40) {
            return "Moderate similarity detected. It's recommended to review the text for improperly cited sources or significant overlap.";
        } else if (percentage >= 10) {
            return "Some similarities were found, but this may be due to common phrases or standard terminology. A quick review is advised.";
        } else {
            return "The text appears to be largely original with a very low-risk of plagiarism. No significant matches were found in the database.";
        }
    }
    
    // --- HTTP Response Helpers ---

    private static void sendSuccessResponse(HttpExchange exchange, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes(StandardCharsets.UTF_8));
        }
    }
    
    private static void sendErrorResponse(HttpExchange exchange, int statusCode, String errorMessage) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, errorMessage.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(errorMessage.getBytes(StandardCharsets.UTF_8));
        }
    }
}